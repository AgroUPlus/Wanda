package com.wander.android.ui.screens.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawInitialQuery = savedStateHandle.get<String>("query").orEmpty()
    private val decodedInitialQuery = runCatching { URLDecoder.decode(rawInitialQuery, "UTF-8") }.getOrDefault(rawInitialQuery)

    private val _query = MutableStateFlow(decodedInitialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sourceFilter = MutableStateFlow<SourceType?>(null)
    val sourceFilter: StateFlow<SourceType?> = _sourceFilter.asStateFlow()

    /** Only sources that can actually search are offered as filters. */
    val availableSources: List<SourceType> = musicRepository.sources
        .filter { it.capabilities.search }
        .map { it.sourceType }
        .sorted()

    private val searchResults: StateFlow<SearchUiState> = _query
        .debounce { if (it.isBlank()) 0L else DEBOUNCE_MS }
        .flatMapLatest { query ->
            flow {
                if (query.isBlank()) {
                    emit(SearchUiState())
                    return@flow
                }
                emit(SearchUiState(isSearching = true, hasQuery = true))
                emit(
                    SearchUiState(
                        isSearching = false,
                        results = musicRepository.searchAllSources(query),
                        hasQuery = true
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    val uiState: StateFlow<SearchUiState> = combine(searchResults, _sourceFilter) { state, filter ->
        if (filter == null) state else state.copy(results = state.results.filter { it.source == filter })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) { _query.value = value }

    fun selectSource(source: SourceType?) { _sourceFilter.value = source }

    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    private companion object {
        const val DEBOUNCE_MS = 350L
    }
}
