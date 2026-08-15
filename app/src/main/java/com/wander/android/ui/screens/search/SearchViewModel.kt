package com.wander.android.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class SearchUiState(
    val isSearching: Boolean = false,
    val results: List<UnifiedTrack> = emptyList(),
    val hasQuery: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection,
    private val shareRepository: ShareRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawInitialQuery = savedStateHandle.get<String>("query").orEmpty()
    private val decodedInitialQuery = runCatching { URLDecoder.decode(rawInitialQuery, "UTF-8") }.getOrDefault(rawInitialQuery)

    private val _query = MutableStateFlow(decodedInitialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The backends this search queries. Everything except the Internet Archive by default: its
     * search is slow enough to hold up every other source's results, and it is the one backend
     * that is not the user's own music.
     */
    private val _selectedSources = MutableStateFlow(
        searchableSources()
            .filterNot { it == SourceType.INTERNET_ARCHIVE }
            .toSet()
    )
    val selectedSources: StateFlow<Set<SourceType>> = _selectedSources.asStateFlow()

    /**
     * Only sources that can search *and* are set up right now. A backend the user signed out of
     * has nothing to offer, so offering its chip — pre-selected, no less — only promised results
     * that could never arrive.
     */
    val availableSources: List<SourceType> = searchableSources().sorted()

    private fun searchableSources() = musicRepository.sources
        .filter { it.capabilities.search && it.isConfigured.value }
        .map { it.sourceType }

    // Re-runs when the sources change as well as the query: toggling a backend on has to go and
    // ask it, not just unhide results that were never fetched.
    private val searchResults: StateFlow<SearchUiState> = combine(_query, _selectedSources, ::Pair)
        .debounce { (query, _) -> if (query.isBlank()) 0L else DEBOUNCE_MS }
        .flatMapLatest { (query, sources) ->
            flow {
                if (query.isBlank()) {
                    emit(SearchUiState())
                    return@flow
                }
                emit(SearchUiState(isSearching = true, hasQuery = true))
                emit(
                    SearchUiState(
                        isSearching = false,
                        results = musicRepository.searchAllSources(query, sources),
                        hasQuery = true
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    // Search results are a snapshot taken before any like happened, so the liked set is overlaid
    // from Room — otherwise tapping the heart wrote through but left the icon unchanged.
    val uiState: StateFlow<SearchUiState> = combine(
        searchResults,
        musicRepository.getLikedTrackIdsFlow()
    ) { state, likedIds ->
        state.copy(results = state.results.map { it.copy(isLiked = it.id in likedIds) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) { _query.value = value }

    fun toggleSource(source: SourceType) {
        _selectedSources.update { current ->
            if (source in current) {
                // Never empty: a search with no sources would just silently return nothing.
                current.minus(source).ifEmpty { current }
            } else {
                current + source
            }
        }
    }

    fun selectAllSources() { _selectedSources.value = availableSources.toSet() }

    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    /** Plays the track, then fills the queue behind it with its source's radio. */
    fun startRadio(track: UnifiedTrack) {
        viewModelScope.launch {
            playerConnection.play(listOf(track))
            val radio = musicRepository.generateRadio(track)
            if (radio.isNotEmpty()) playerConnection.addToQueue(radio)
        }
    }

    /** Whether this track's backend can mint a public link at all. */
    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    /** The link is published on a shared flow and raised as a share sheet by `WanderApp`. */
    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    private companion object {
        const val DEBOUNCE_MS = 350L
    }
}
