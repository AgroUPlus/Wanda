package com.wander.android.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.PlaylistWriteRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import com.wander.android.data.sources.local.LocalMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The library's tabs.
 *
 * History is deliberately not among them. It is not a *collection* — it is a log, it is never
 * curated, and it was costing a sixth of a tab row that was already clipping its labels. It lives
 * behind an icon in the header instead, the way Settings does on Home.
 */
enum class LibraryTab(val label: String) {
    TRACKS("Tracks"),
    LIKED("Liked"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
    DOWNLOADS("Offline")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val localSource: LocalMusicSource,
    private val playerConnection: PlayerConnection,
    private val shareRepository: ShareRepository,
    private val playlistWriter: PlaylistWriteRepository
) : ViewModel() {

    /** Whether any connected source can be written to. Drives the "New playlist" affordance. */
    val canCreatePlaylists: Boolean
        get() = SourceType.entries.any(playlistWriter::canWrite)

    /** Creates an empty playlist, then refreshes so it appears in the list. */
    fun createPlaylist(name: String) {
        val target = SourceType.entries.firstOrNull(playlistWriter::canWrite) ?: return
        viewModelScope.launch {
            playlistWriter.createPlaylist(target, name, emptyList())
            _playlists.value = musicRepository.getPlaylists()
        }
    }

    private val _tab = MutableStateFlow(LibraryTab.TRACKS)
    val tab: StateFlow<LibraryTab> = _tab.asStateFlow()

    private val _sourceFilter = MutableStateFlow<SourceType?>(null)
    val sourceFilter: StateFlow<SourceType?> = _sourceFilter.asStateFlow()

    val availableSources: List<SourceType> = musicRepository.sources.map { it.sourceType }.sorted()

    /**
     * One flow per tab rather than one flow keyed on the selected tab. The pager keeps the
     * neighbouring pages composed, so a single tab-dependent flow would render the wrong list on
     * the page sliding into view.
     *
     * All Room-backed, so the library is fully usable with no network.
     */
    val tracks: Flow<PagingData<UnifiedTrack>> = _sourceFilter
        .flatMapLatest { filter -> musicRepository.pagedLibraryTracks(filter) }
        // Survives the tab pager recomposing and the screen being rotated, so scrolling back does
        // not refetch pages already on screen.
        .cachedIn(viewModelScope)

    /**
     * Plays [track] with the rest of the library queued around it.
     *
     * The list is fetched here rather than held, because with paging the screen no longer has it —
     * and that is the point: the position is needed once, on a tap, and keeping a thousand rows in
     * memory to avoid one query is what paging was removing.
     */
    fun playFromLibrary(track: UnifiedTrack) {
        viewModelScope.launch {
            val ids = musicRepository.libraryTrackIds(_sourceFilter.value)
            val index = ids.indexOf(track.id)
            // Not found is possible and ordinary: the row was queued from a page loaded before a
            // sync removed it. Playing the one track alone is better than playing nothing.
            if (index < 0) playerConnection.play(listOf(track), 0) else playQueue(ids, index)
        }
    }

    private suspend fun playQueue(ids: List<String>, index: Int) {
        // `getTracksByIds` answers in whatever order SQLite pleases, not in `ids` order, so the
        // rows are put back in the list's order before being queued — otherwise "play from here"
        // would start at the right song and then continue through a shuffled library.
        val byId = musicRepository.tracksByIds(ids).associateBy { it.id }
        val ordered = ids.mapNotNull(byId::get)
        val position = ordered.indexOfFirst { it.id == ids[index] }.coerceAtLeast(0)
        playerConnection.play(ordered, position)
    }

    val likedTracks: StateFlow<List<UnifiedTrack>> = musicRepository.getLikedTracksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What has been played on this device, newest first. Room-backed, so it works offline. */
    val historyTracks: StateFlow<List<UnifiedTrack>> = musicRepository.getRecentlyPlayedFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedTracks: StateFlow<List<UnifiedTrack>> = musicRepository.getDownloadedTracksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The handful of records added most recently, for the row above the grid.
     *
     * Ordered by the id list rather than by the album list, because the id list *is* the
     * ordering — a `mapNotNull` over the albums would silently hand back alphabetical order.
     */
    val recentAlbums: StateFlow<List<UnifiedAlbum>> = combine(
        musicRepository.getAlbumsFlow(),
        musicRepository.getRecentlyAddedAlbumIdsFlow()
    ) { albums, recentIds ->
        val byId = albums.associateBy { it.id }
        recentIds.mapNotNull(byId::get)
    }
        // The two flows emit independently, so a scan that touches both produces an intermediate
        // pairing — new ids against stale albums, or the reverse — that resolves to a list
        // identical to the one already on screen. Without this the row rebuilds and its scroll
        // position jumps for an update that changed nothing.
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<UnifiedAlbum>> = musicRepository.getAlbumsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _playlists = MutableStateFlow<List<UnifiedPlaylist>>(emptyList())
    val playlists: StateFlow<List<UnifiedPlaylist>> = _playlists.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            localSource.refresh()
            musicRepository.refreshAlbums()
            // Without this the Tracks tab only ever held what some other screen happened to have
            // persisted — browsing an album, or playing a search result. Pulling recent tracks is
            // what actually fills the library from the connected servers.
            musicRepository.getRecentTracks(LIBRARY_TRACK_REFRESH)
            _playlists.value = musicRepository.getPlaylists()
            _isRefreshing.value = false
        }
    }

    fun selectTab(tab: LibraryTab) { _tab.value = tab }

    fun selectSource(source: SourceType?) { _sourceFilter.value = source }

    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun openPlaylist(playlist: UnifiedPlaylist) {
        viewModelScope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun playPlaylistNext(playlist: UnifiedPlaylist) {
        viewModelScope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.playNext(tracks)
        }
    }

    fun addPlaylistToQueue(playlist: UnifiedPlaylist) {
        viewModelScope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.addToQueue(tracks)
        }
    }

    fun addPlaylistToAnother(playlist: UnifiedPlaylist, controller: com.wander.android.ui.components.AddToPlaylistController) {
        viewModelScope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) {
                controller.openForTracks(tracks, playlist.source)
            }
        }
    }

    // ── Albums ──────────────────────────────────────────────────────────────────────────────
    //
    // The same three verbs as playlists, against the same repository call. A record and a playlist
    // are both "a list of tracks somebody assembled" as far as the queue is concerned.

    fun playAlbum(album: UnifiedAlbum) {
        viewModelScope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun playAlbumNext(album: UnifiedAlbum) {
        viewModelScope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.playNext(tracks)
        }
    }

    fun addAlbumToQueue(album: UnifiedAlbum) {
        viewModelScope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.addToQueue(tracks)
        }
    }

    fun addAlbumToPlaylist(album: UnifiedAlbum, controller: com.wander.android.ui.components.AddToPlaylistController) {
        viewModelScope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) controller.openForTracks(tracks, album.source)
        }
    }

    /**
     * Shares the album as a link that names no backend.
     *
     * Unconditional, unlike [canShare] for a track: the link describes the record rather than
     * pointing at a server, so it works from a source that cannot mint links at all.
     */
    fun shareAlbum(album: UnifiedAlbum) = shareRepository.shareAlbum(album)

    fun deletePlaylist(playlist: UnifiedPlaylist) {
        viewModelScope.launch {
            playlistWriter.deletePlaylist(playlist).onSuccess {
                _playlists.value = musicRepository.getPlaylists()
            }
        }
    }

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

    /** The same question for a playlist, which has no `UnifiedTrack` to ask about. */
    fun canShare(source: SourceType): Boolean = shareRepository.canShare(source)

    fun sharePlaylist(playlist: UnifiedPlaylist) {
        viewModelScope.launch {
            shareRepository.share(
                ShareTarget(
                    kind = ShareKind.PLAYLIST,
                    source = playlist.source,
                    id = playlist.id,
                    title = playlist.name
                )
            )
        }
    }

    /** The link is published on a shared flow and raised as a share sheet by `WanderApp`. */
    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    fun deleteDownloadedTrack(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.deleteDownloadedTrack(track.id) }
    }

    private companion object {
        /** How many recently added tracks a refresh pulls into Room from every active source. */
        const val LIBRARY_TRACK_REFRESH = 200
    }
}
