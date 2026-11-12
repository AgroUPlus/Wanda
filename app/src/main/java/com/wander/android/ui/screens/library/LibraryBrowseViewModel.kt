package com.wander.android.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wander.android.core.database.dao.LibraryArtistRow
import com.wander.android.core.database.dao.LibraryGenreRow
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
import kotlinx.coroutines.flow.flowOf
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
    private val prefs: LibraryBrowsePrefs,
    private val savedState: SavedStateHandle
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

    private val _views = MutableStateFlow(
        LibraryTab.entries.filter { it.offersGrid }.associateWith { prefs.viewFor(it, it.defaultView) }
    )

    /** List or grid, for each tab that [offersGrid]. A tab missing here is always a list. */
    val views: StateFlow<Map<LibraryTab, LibraryView>> = _views.asStateFlow()

    fun setView(tab: LibraryTab, view: LibraryView) {
        if (!tab.offersGrid) return
        _views.update { it + (tab to view) }
        prefs.setView(tab, view)
    }

    /**
     * Whether the track lists that [offersDownloadedFilter] show only what is on the device. One
     * choice for every such tab, as the source filter is.
     */
    private val _downloadedOnly = MutableStateFlow(false)
    val downloadedOnly: StateFlow<Boolean> = _downloadedOnly.asStateFlow()

    fun setDownloadedOnly(on: Boolean) {
        _downloadedOnly.value = on
    }

    // Configured sources only — not every registered backend, the same accessor Home's own source
    // filter uses. A source no one is signed into (Deezer before login, say) has nothing to filter
    // by and reads as a broken chip that always returns zero tracks.
    val availableSources: List<SourceType> = musicRepository.configuredSources().sorted()

    /** The sources every list is narrowed to; empty for "every source". */
    private val sources = MutableStateFlow<Set<SourceType>>(emptySet())

    /**
     * The one source the filter row shows as chosen, null for "All" — Home's row and its rule, so
     * the two filters look and answer alike. Kept as a set underneath: every list narrows by one.
     */
    val selectedSource: StateFlow<SourceType?> = sources
        .map { it.singleOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** [source] alone, or every source (including one that connects later) for null. */
    fun selectSource(source: SourceType?) {
        sources.value = source?.let(::setOf) ?: emptySet()
    }

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

    /**
     * Liked as its page lists it: every liked track, or with [downloadedOnly] the ones the Offline
     * list would hold too — the same rule as `observeDownloadedTracks`. [likedTracks] stays whole
     * for the shelf's count.
     */
    val shownLikedTracks: StateFlow<List<UnifiedTrack>> = combine(likedTracks, _downloadedOnly) { tracks, only ->
        if (only) tracks.filter { it.isDownloaded && it.source != SourceType.LOCAL && !it.isEpisode } else tracks
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The Tracks tab with [downloadedOnly]: the Offline list in the Tracks tab's own order. Read
     * as a whole list rather than paged — it is what is on the device, not the whole library.
     */
    val downloadedLibraryTracks: StateFlow<List<UnifiedTrack>> =
        combine(_downloadedOnly, sortOf(LibraryTab.TRACKS), sources, ::Triple)
            .flatMapLatest { (only, sort, filter) ->
                if (only) browse.downloadedTracks(sort, filter) else flowOf(emptyList())
            }
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

    val artists: StateFlow<List<LibraryArtistRow>> = sources
        .flatMapLatest { filter -> browse.artists(filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val genres: StateFlow<List<LibraryGenreRow>> = sources
        .flatMapLatest { filter -> browse.genres(filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The genre the Genres page has open, or null for the list of genres. */
    val openGenre: StateFlow<String?> = savedState.getStateFlow<String?>(KEY_GENRE, null)

    fun openGenre(genre: String?) { savedState[KEY_GENRE] = genre }

    val genreTracks: StateFlow<List<UnifiedTrack>> = combine(openGenre, sources, ::Pair)
        .flatMapLatest { (genre, filter) -> genre?.let { browse.genreTracks(it, filter) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the Tracks tab has anything for Play all to play. */
    val hasTracks: StateFlow<Boolean> = sources
        .flatMapLatest { filter -> browse.hasTracks(filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val tracksSort: LibrarySort
        get() = _sorts.value[LibraryTab.TRACKS] ?: LibraryTab.TRACKS.defaultSort

    /** Plays [track] with the rest of the Tracks tab, in the order and filter it is shown in. */
    fun playFromLibrary(track: UnifiedTrack) =
        libraryPlayback.playFromLibrary(viewModelScope, track, tracksSort, sources.value)

    fun playAllTracks() = libraryPlayback.playLibrary(viewModelScope, tracksSort, sources.value, shuffled = false)

    fun shuffleAllTracks() = libraryPlayback.playLibrary(viewModelScope, tracksSort, sources.value, shuffled = true)

    private companion object {
        const val KEY_GENRE = "library_open_genre"
    }
}
