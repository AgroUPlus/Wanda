package com.wander.android.ui.screens.search

import com.wander.android.data.model.SearchKind
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.SearchQueryHolder
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
    private val queryHolder: SearchQueryHolder
) : ViewModel() {

    /**
     * The field lives in the dock at the bottom of the app, which outlives this ViewModel, so the
     * text is owned by [SearchQueryHolder] and only observed here. It is also what decides whether
     * this screen is on show at all — see `LibrarySurface`.
     */
    val query: StateFlow<String> = queryHolder.query

    /**
     * The backends a search can reach right now.
     *
     * A flow, not a snapshot. This used to read `isConfigured.value` once when the ViewModel was
     * constructed, so a backend that finished signing in a moment later never gained a chip and
     * was never searched until the screen was recreated — and since the search surface is now part
     * of the library destination, that could be a long time.
     *
     * Keyed on `isSearchable` rather than `isConfigured`: YouTube Music answers a search without
     * an account, and excluding it while signed out was the reason its results went missing.
     */
    val availableSources: StateFlow<List<SourceType>> =
        combine(musicRepository.sources.map { it.isSearchable }) { flags ->
            musicRepository.sources
                .filterIndexed { index, source -> flags[index] && source.capabilities.search }
                .map { it.sourceType }
                .sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Which of those the user has narrowed to, or null for "whatever is available".
     *
     * Null is not the same as the full set. A user who has never touched the chips wants every
     * source, *including* one that connects later; a user who explicitly selected every chip has
     * made a choice about the sources that existed then. Only the second is a set.
     */
    private val _selectedSources = MutableStateFlow<Set<SourceType>?>(null)

    val selectedSources: StateFlow<Set<SourceType>> =
        combine(_selectedSources, availableSources) { explicit, available ->
            explicit?.intersect(available.toSet())?.ifEmpty { available.toSet() }
                ?: available.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * What kind of thing this search is for. Only YouTube Music serves anything but [
     * SearchKind.TRACKS]; the other backends answer the non-music kinds with nothing rather than
     * with songs, so the results stay honest to the chip that is selected.
     */
    private val _kind = MutableStateFlow(SearchKind.TRACKS)
    val kind: StateFlow<SearchKind> = _kind.asStateFlow()

    fun selectKind(kind: SearchKind) { _kind.value = kind }

    // Re-runs when the sources change as well as the query: toggling a backend on has to go and
    // ask it, not just unhide results that were never fetched.
    private val searchResults: StateFlow<SearchUiState> = combine(
        query,
        // The *resolved* selection, not the raw nullable one. While the user has made no explicit
        // choice the raw flow holds a constant null, so a backend finishing its sign-in mid-search
        // changed what "everything" means without this flow noticing — the results stayed as they
        // were until the next keystroke.
        selectedSources,
        _kind
    ) { query, sources, kind -> Triple(query, sources, kind) }
        .debounce { (query, _, _) -> if (query.isBlank()) 0L else DEBOUNCE_MS }
        .flatMapLatest { (query, sources, kind) ->
            flow {
                if (query.isBlank()) {
                    emit(SearchUiState())
                    return@flow
                }
                emit(SearchUiState(isSearching = true, hasQuery = true))
                emit(
                    SearchUiState(
                        isSearching = false,
                        results = musicRepository.searchAllSources(query, sources, kind),
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

    fun toggleSource(source: SourceType) {
        val current = selectedSources.value
        _selectedSources.value = if (source in current) {
            // Never empty: a search with no sources would just silently return nothing.
            current.minus(source).ifEmpty { current }
        } else {
            current + source
        }
    }

    /** Back to "everything", including sources that connect later — see [_selectedSources]. */
    fun selectAllSources() { _selectedSources.value = null }

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
