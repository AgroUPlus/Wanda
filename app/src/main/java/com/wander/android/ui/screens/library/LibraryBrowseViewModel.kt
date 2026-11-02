package com.wander.android.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wander.android.data.model.LibrarySort
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.LibraryBrowseRepository
import com.wander.android.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The Library's lists — what each tab shows, in which order, from which sources.
 *
 * Apart from [LibraryViewModel], which keeps the sections, the refresh and what a long-press does:
 * both resolve to one instance per Library destination, so the pages and the screen agree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryBrowseViewModel @Inject constructor(
    musicRepository: MusicRepository,
    private val browse: LibraryBrowseRepository,
    private val libraryPlayback: LibraryPlaybackCoordinator,
    private val prefs: LibraryBrowsePrefs
) : ViewModel() {

    private val _sorts = MutableStateFlow(
        LibraryTab.entries
            .filter { it.sortOptions.isNotEmpty() }
            .associateWith { tab ->
                // A stored order the tab no longer offers falls back rather than sorting by it.
                prefs.sortFor(tab, tab.defaultSort).takeIf(tab.sortOptions::contains) ?: tab.defaultSort
            }
    )

    /** The order each sortable tab is in. A tab missing here has no order to choose. */
    val sorts: StateFlow<Map<LibraryTab, LibrarySort>> = _sorts.asStateFlow()

    fun setSort(tab: LibraryTab, sort: LibrarySort) {
        if (sort !in tab.sortOptions) return
        _sorts.update { it + (tab to sort) }
        prefs.setSort(tab, sort)
    }

    private fun sortOf(tab: LibraryTab): Flow<LibrarySort> =
        _sorts.map { it[tab] ?: tab.defaultSort }.distinctUntilChanged()

    // Configured sources only — not every registered backend, the same accessor Home's own source
    // filter uses. A source no one is signed into (Deezer before login, say) has nothing to filter
    // by and reads as a broken chip that always returns zero tracks.
    val availableSources: List<SourceType> = musicRepository.configuredSources().sorted()

    /** The sources every list is narrowed to; empty for "every source", see [selectAllSources]. */
    private val sources = MutableStateFlow<Set<SourceType>>(emptySet())

    /** What the filter row shows as on: every source while nothing is narrowed. */
    val selectedSources: StateFlow<Set<SourceType>> = sources
        .map { it.ifEmpty { availableSources.toSet() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, availableSources.toSet())

    /** Search's rule, so the two rows behave alike: never down to no source at all. */
    fun toggleSource(source: SourceType) {
        val current = selectedSources.value
        val next = if (source in current) current.minus(source).ifEmpty { current } else current + source
        sources.value = if (next.containsAll(availableSources)) emptySet() else next
    }

    /** Back to every source, including one that connects later. */
    fun selectAllSources() { sources.value = emptySet() }

    /**
     * The narrowing itself, empty for every source. Playlists are not all in Room — a backend's
     * come from the network — so that page filters what it holds against this, where the other
     * lists are narrowed in SQL.
     */
    val sourceFilter: StateFlow<Set<SourceType>> = sources.asStateFlow()

    val tracks: Flow<PagingData<UnifiedTrack>> = combine(sortOf(LibraryTab.TRACKS), sources, ::Pair)
        .flatMapLatest { (sort, filter) -> browse.pagedTracks(sort, filter) }
        .cachedIn(viewModelScope)

    val likedTracks: StateFlow<List<UnifiedTrack>> = combine(sortOf(LibraryTab.LIKED), sources, ::Pair)
        .flatMapLatest { (sort, filter) -> browse.likedTracks(sort, filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedTracks: StateFlow<List<UnifiedTrack>> = combine(sortOf(LibraryTab.DOWNLOADS), sources, ::Pair)
        .flatMapLatest { (sort, filter) -> browse.downloadedTracks(sort, filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<UnifiedAlbum>> = combine(sortOf(LibraryTab.ALBUMS), sources, ::Pair)
        .flatMapLatest { (sort, filter) -> browse.albums(sort, filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The Recent row, drawn from [albums] so it honours the same source filter. */
    val recentAlbums: StateFlow<List<UnifiedAlbum>> = combine(
        albums,
        musicRepository.getRecentlyAddedAlbumIdsFlow()
    ) { albums, recentIds ->
        val byId = albums.associateBy { it.id }
        recentIds.mapNotNull(byId::get)
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Plays [track] with the rest of the Tracks tab, in the order and filter it is shown in. */
    fun playFromLibrary(track: UnifiedTrack) {
        val sort = _sorts.value[LibraryTab.TRACKS] ?: LibraryTab.TRACKS.defaultSort
        libraryPlayback.playFromLibrary(viewModelScope, track, sort, sources.value)
    }
}
