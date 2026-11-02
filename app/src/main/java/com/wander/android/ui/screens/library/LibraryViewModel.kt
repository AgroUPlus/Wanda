package com.wander.android.ui.screens.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.network.userMessageRes
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
import com.wander.android.ui.navigation.NavLayoutStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val localSource: LocalMusicSource,
    private val playerConnection: PlayerConnection,
    playbackCoordinator: PlaybackCoordinator,
    private val shareRepository: ShareRepository,
    private val playlistWriter: PlaylistWriteRepository,
    private val libraryPlayback: LibraryPlaybackCoordinator,
    private val playlistsLoader: LibraryPlaylistsLoader,
    navLayout: NavLayoutStore,
    private val savedState: SavedStateHandle,
    private val browsePrefs: LibraryBrowsePrefs
) : ViewModel() {

    /** Whether any connected source can be written to. Drives the "New playlist" affordance. */
    val canCreatePlaylists: Boolean
        get() = SourceType.entries.any(playlistWriter::canWrite)

    /** Where a plain new playlist goes: the first source that can hold one, as it always has. */
    val newPlaylistSource: SourceType
        get() = SourceType.entries.firstOrNull(playlistWriter::canWrite) ?: SourceType.LOCAL

    /** Re-reads the list, so a playlist the new-playlist sheet just made appears in it. */
    fun refreshPlaylists() {
        viewModelScope.launch { playlistsLoader.refresh() }
    }

    /** The sections the user chose to show, in their order. */
    val tabs: StateFlow<List<LibraryTab>> =
        navLayout.libraryTabs.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryTab.entries)

    /**
     * The open section: the saved state first (process death), then the last one chosen (a cold
     * start), then Liked. The pager drives this on every settle, so it is written back from there.
     */
    val tab: StateFlow<LibraryTab> = savedState.getStateFlow(
        KEY_TAB,
        browsePrefs.lastTab() ?: LibraryTab.LIKED
    )

    val playlists: StateFlow<List<UnifiedPlaylist>> = playlistsLoader.playlists

    /** A @StringRes saying why the playlists could not be read, or null. */
    val playlistsError: StateFlow<Int?> = playlistsLoader.error

    /** A @StringRes saying why the albums could not be refreshed, or null. Room's albums stay shown. */
    private val _albumsError = MutableStateFlow<Int?>(null)
    val albumsError: StateFlow<Int?> = _albumsError.asStateFlow()

    /** A refresh the user asked for by pulling: the only one that shows the spinner. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Any refresh at all, including the one every visit starts on its own. Only an empty list
     * shows it, as skeleton rows: what Room already holds is on screen at once, and a spinner over
     * it for the length of a network round trip read as the library still loading when it wasn't.
     */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        playlistsLoader.start(viewModelScope)
        refresh(pulled = false)
    }

    fun refresh() = refresh(pulled = true)

    /**
     * Playlists and the rest side by side: the playlist tab used to wait behind a scan of the
     * device's files and every source's albums before it showed anything at all.
     *
     * The scan and the album refresh are the slow half; see [CatalogueRefreshGate].
     */
    private fun refresh(pulled: Boolean) {
        viewModelScope.launch {
            _isSyncing.value = true
            if (pulled) _isRefreshing.value = true
            val playlists = launch { playlistsLoader.refresh() }
            val catalogue = launch {
                if (pulled || CatalogueRefreshGate.due()) {
                    localSource.refresh()
                    musicRepository.refreshAlbums()
                        .onSuccess { _albumsError.value = null }
                        .onFailure { _albumsError.value = it.userMessageRes() }
                    musicRepository.getRecentTracks(LIBRARY_TRACK_REFRESH)
                    musicRepository.importRemoteLikes()
                }
            }
            playlists.join()
            catalogue.join()
            _isRefreshing.value = false
            _isSyncing.value = false
            backfillAlbumTracks()
        }
    }

    private suspend fun backfillAlbumTracks() {
        repeat(BACKFILL_SLICES) {
            if (musicRepository.importMissingAlbumTracks() == 0) return
        }
    }

    fun selectTab(tab: LibraryTab) {
        if (tab == this.tab.value) return
        savedState[KEY_TAB] = tab
        browsePrefs.setLastTab(tab)
    }

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
            // The list follows Room on its own; a backend's playlist is a network list, re-read here.
            playlistWriter.deletePlaylist(playlist).onSuccess { playlistsLoader.refresh() }
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

    private companion object {
        const val LIBRARY_TRACK_REFRESH = 200
        const val KEY_TAB = "library_tab"
        private const val BACKFILL_SLICES = 12
    }
}
