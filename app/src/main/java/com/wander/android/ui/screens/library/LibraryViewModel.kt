package com.wander.android.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.sources.local.LocalMusicSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    TRACKS("Tracks"), LIKED("Liked"), ALBUMS("Albums"), PLAYLISTS("Playlists"), DOWNLOADS("Offline")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val localSource: LocalMusicSource,
    private val playerConnection: PlayerConnection
) : ViewModel() {

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
    val tracks: StateFlow<List<UnifiedTrack>> = _sourceFilter
        .flatMapLatest { filter ->
            filter
                ?.let(musicRepository::getTracksBySourceFlow)
                ?: musicRepository.getAllTracksFlow()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedTracks: StateFlow<List<UnifiedTrack>> = musicRepository.getLikedTracksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedTracks: StateFlow<List<UnifiedTrack>> = musicRepository.getDownloadedTracksFlow()
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
            _playlists.value = musicRepository.getPlaylists()
            _isRefreshing.value = false
        }
    }

    fun selectTab(tab: LibraryTab) { _tab.value = tab }

    fun selectSource(source: SourceType?) { _sourceFilter.value = source }

    fun play(tracks: List<UnifiedTrack>, index: Int) = playerConnection.play(tracks, index)

    fun openAlbum(album: UnifiedAlbum) {
        viewModelScope.launch {
            val tracks = musicRepository.getAlbumTracks(album)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun openPlaylist(playlist: UnifiedPlaylist) {
        viewModelScope.launch {
            val tracks = musicRepository.getPlaylistTracks(playlist)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }
}
