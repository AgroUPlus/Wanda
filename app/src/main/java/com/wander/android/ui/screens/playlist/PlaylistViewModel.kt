package com.wander.android.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.work.ImportWorkState
import com.wander.android.core.work.PlaylistImportScheduler
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.PlaylistWriteRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository,
    private val playerConnection: PlayerConnection,
    private val playbackCoordinator: PlaybackCoordinator,
    private val importScheduler: PlaylistImportScheduler,
    private val sharedPlaylists: SharedPlaylistRepository,
    private val playlistWrites: PlaylistWriteRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>("playlistId")
        .orEmpty()
        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    private val _playlist = MutableStateFlow<UnifiedPlaylist?>(null)
    val playlist: StateFlow<UnifiedPlaylist?> = _playlist.asStateFlow()

    private val _tracks = MutableStateFlow<List<UnifiedTrack>>(emptyList())
    val tracks: StateFlow<List<UnifiedTrack>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refresh()
    }

    /** Background matching of an imported playlist. Idle for every other playlist. */
    val importWork: StateFlow<ImportWorkState> = importScheduler.observe(playlistId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ImportWorkState())

    init {
        // The tracks are read once, so a track the worker has just matched only shows up on a
        // reload. WorkManager reports progress after every track; reloading on that is event-driven,
        // where polling would not be.
        viewModelScope.launch { importWork.drop(1).collect { refresh() } }
        // A shared playlist changes under this screen — an edit here, a collaborator's, a sync —
        // and every one of those lands in its copy in Room first.
        viewModelScope.launch {
            sharedPlaylists.observeItems(playlistId).distinctUntilChanged().drop(1).collect { refresh() }
        }
    }

    private val _isRefreshing = MutableStateFlow(false)

    /** A reload the person asked for by pulling, as opposed to the ones that follow an edit or a sync. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun pullToRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            load()
            _isRefreshing.value = false
        }
    }

    private suspend fun load() {
        _isLoading.value = true
        val pl = musicRepository.getPlaylistById(playlistId)
        _playlist.value = pl
        val list = sharedTracks() ?: musicRepository.getPlaylistTracksById(playlistId)
        _tracks.value = list
        if (pl != null && pl.coverArtUrl == null) {
            val fallbackCover = list.firstNotNullOfOrNull { it.artworkUrl }
            if (fallbackCover != null) {
                _playlist.value = pl.copy(coverArtUrl = fallbackCover)
            }
        }
        _isLoading.value = false
    }

    /**
     * A shared playlist's tracks come from its copy, one per item, so a position on screen is
     * always the position an edit names. Null when it is not shared, or its copy is not read yet.
     */
    private suspend fun sharedTracks(): List<UnifiedTrack>? {
        val agroId = sharedPlaylists.agroIdFor(playlistId) ?: return null
        return sharedPlaylists.tracks(agroId).map { it.track }
            .takeIf { it.isNotEmpty() || playlistId.startsWith(SourceType.AGRO.idPrefix) }
    }

    /** What can actually be played: an import's still-unmatched placeholders have nothing behind them. */
    private fun playable() = _tracks.value.filter { it.source != SourceType.UNRESOLVED }

    fun playAll() = playable().takeIf { it.isNotEmpty() }?.let { playerConnection.play(it) }

    fun shuffle() = playable().takeIf { it.isNotEmpty() }
        ?.let { playerConnection.play(it.shuffled()) }

    fun play(index: Int) {
        val queue = playable()
        val start = _tracks.value.getOrNull(index)?.let(queue::indexOf) ?: return
        if (start >= 0) playerConnection.play(queue, start)
    }

    /**
     * Shows a dragged row's move at once, before it is written, and says whether it could. The
     * write's own reload follows either way, so a move the backend refused is put back.
     */
    fun showMove(from: Int, to: Int): Boolean {
        val list = _tracks.value
        if (from !in list.indices || to !in list.indices) return false
        _tracks.value = list.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    /** Whether the open playlist's name and description can be changed here. */
    fun canEditDetails(): Boolean = _playlist.value?.let(playlistWrites::canEditDetails) == true

    fun editDetails(name: String, description: String) {
        val current = _playlist.value ?: return
        viewModelScope.launch {
            playlistWrites.updateDetails(current, name, description).onSuccess { refresh() }
        }
    }

    /** Matches the placeholders that were not found, again. */
    fun retryImport() = importScheduler.enqueue(playlistId)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    fun startRadio(track: UnifiedTrack) {
        viewModelScope.launch { playbackCoordinator.startRadio(track) }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }
}
