package com.wander.android.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.work.ImportWorkState
import com.wander.android.core.work.PlaylistImportScheduler
import com.wander.android.data.sources.agro.PlaylistVisibility
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val pl = musicRepository.getPlaylistById(playlistId)
            _playlist.value = pl
            val list = musicRepository.getPlaylistTracksById(playlistId)
            _tracks.value = list
            if (pl != null && pl.coverArtUrl == null) {
                val fallbackCover = list.firstNotNullOfOrNull { it.artworkUrl }
                if (fallbackCover != null) {
                    _playlist.value = pl.copy(coverArtUrl = fallbackCover)
                }
            }
            _isLoading.value = false
        }
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

    private val _choosingVisibility = MutableStateFlow(false)

    /** True while the person is picking who can open the playlist they are about to publish. */
    val choosingVisibility: StateFlow<Boolean> = _choosingVisibility.asStateFlow()

    fun dismissVisibilityChoice() {
        _choosingVisibility.value = false
    }

    fun shareWithVisibility(visibility: PlaylistVisibility) {
        _choosingVisibility.value = false
        val pl = _playlist.value ?: return
        val list = _tracks.value
        viewModelScope.launch { shareRepository.shareLocalPlaylist(pl, list, visibility) }
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

    /** A source with its own link offers it; any other playlist with tracks is shared as a described link. */
    fun canSharePlaylist(): Boolean = _playlist.value?.let {
        shareRepository.canShare(it.source) || _tracks.value.isNotEmpty()
    } ?: false

    fun sharePlaylist() {
        val pl = _playlist.value ?: return
        if (!shareRepository.canShare(pl.source)) {
            // With Agro paired the person decides who can open it; otherwise it is just a link.
            if (shareRepository.canPublishToAgro) {
                _choosingVisibility.value = true
            } else {
                val list = _tracks.value
                viewModelScope.launch { shareRepository.shareLocalPlaylist(pl, list) }
            }
            return
        }
        viewModelScope.launch {
            shareRepository.share(
                ShareTarget(
                    kind = ShareKind.PLAYLIST,
                    source = pl.source,
                    id = pl.id,
                    title = pl.name,
                    subtitle = pl.comment
                )
            )
        }
    }
}
