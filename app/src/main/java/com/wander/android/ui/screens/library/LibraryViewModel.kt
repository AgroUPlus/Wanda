package com.wander.android.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
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
import com.wander.android.ui.components.AddToPlaylistController
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
import kotlinx.coroutines.flow.flowOn
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val localSource: LocalMusicSource,
    private val playerConnection: PlayerConnection,
    playbackCoordinator: PlaybackCoordinator,
    private val shareRepository: ShareRepository,
    private val playlistWriter: PlaylistWriteRepository,
    private val libraryPlayback: LibraryPlaybackCoordinator
) : ViewModel() {

    constructor(
        musicRepository: MusicRepository,
        localSource: LocalMusicSource,
        playerConnection: PlayerConnection,
        playbackCoordinator: PlaybackCoordinator,
        shareRepository: ShareRepository,
        playlistWriter: PlaylistWriteRepository
    ) : this(
        musicRepository = musicRepository,
        localSource = localSource,
        playerConnection = playerConnection,
        playbackCoordinator = playbackCoordinator,
        shareRepository = shareRepository,
        playlistWriter = playlistWriter,
        libraryPlayback = LibraryPlaybackCoordinator(playerConnection, playbackCoordinator, musicRepository)
    )

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
     * One flow per tab rather than one flow keyed on the selected tab.
     */
    val tracks: Flow<PagingData<UnifiedTrack>> = _sourceFilter
        .flatMapLatest { filter -> musicRepository.pagedLibraryTracks(filter) }
        .cachedIn(viewModelScope)

    fun playFromLibrary(track: UnifiedTrack) {
        libraryPlayback.playFromLibrary(viewModelScope, track, _sourceFilter.value)
    }

    val likedTracks: StateFlow<List<UnifiedTrack>> = musicRepository.getLikedTracksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedTracks: StateFlow<List<UnifiedTrack>> = musicRepository.getDownloadedTracksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentAlbums: StateFlow<List<UnifiedAlbum>> = combine(
        musicRepository.getAlbumsFlow(),
        musicRepository.getRecentlyAddedAlbumIdsFlow()
    ) { albums, recentIds ->
        val byId = albums.associateBy { it.id }
        recentIds.mapNotNull(byId::get)
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
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
            musicRepository.getRecentTracks(LIBRARY_TRACK_REFRESH)
            _playlists.value = musicRepository.getPlaylists()
            _isRefreshing.value = false
            backfillAlbumTracks()
        }
    }

    private suspend fun backfillAlbumTracks() {
        repeat(BACKFILL_SLICES) {
            if (musicRepository.importMissingAlbumTracks() == 0) return
        }
    }

    fun selectTab(tab: LibraryTab) { _tab.value = tab }

    fun selectSource(source: SourceType?) { _sourceFilter.value = source }

    fun play(tracks: List<UnifiedTrack>, index: Int) = libraryPlayback.play(tracks, index)

    fun openPlaylist(playlist: UnifiedPlaylist) = libraryPlayback.openPlaylist(viewModelScope, playlist)

    fun playPlaylistNext(playlist: UnifiedPlaylist) = libraryPlayback.playPlaylistNext(viewModelScope, playlist)

    fun addPlaylistToQueue(playlist: UnifiedPlaylist) = libraryPlayback.addPlaylistToQueue(viewModelScope, playlist)

    fun addPlaylistToAnother(playlist: UnifiedPlaylist, controller: AddToPlaylistController) =
        libraryPlayback.addPlaylistToAnother(viewModelScope, playlist, controller)

    fun playAlbum(album: UnifiedAlbum) = libraryPlayback.playAlbum(viewModelScope, album)

    fun playAlbumNext(album: UnifiedAlbum) = libraryPlayback.playAlbumNext(viewModelScope, album)

    fun addAlbumToQueue(album: UnifiedAlbum) = libraryPlayback.addAlbumToQueue(viewModelScope, album)

    fun addAlbumToPlaylist(album: UnifiedAlbum, controller: AddToPlaylistController) =
        libraryPlayback.addAlbumToPlaylist(viewModelScope, album, controller)

    fun shareAlbum(album: UnifiedAlbum) = shareRepository.shareAlbum(album)

    fun deletePlaylist(playlist: UnifiedPlaylist) {
        viewModelScope.launch {
            playlistWriter.deletePlaylist(playlist).onSuccess {
                _playlists.value = musicRepository.getPlaylists()
            }
        }
    }

    fun playNext(track: UnifiedTrack) = libraryPlayback.playNext(track)

    fun addToQueue(track: UnifiedTrack) = libraryPlayback.addToQueue(track)

    fun startRadio(track: UnifiedTrack) = libraryPlayback.startRadio(viewModelScope, track)

    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

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
        const val LIBRARY_TRACK_REFRESH = 200
        private const val BACKFILL_SLICES = 12
    }
}
