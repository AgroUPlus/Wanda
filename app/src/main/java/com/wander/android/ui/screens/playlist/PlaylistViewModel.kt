package com.wander.android.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository,
    private val playerConnection: PlayerConnection,
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

    fun playAll() = tracks.value.takeIf { it.isNotEmpty() }?.let { playerConnection.play(it) }

    fun shuffle() = tracks.value.takeIf { it.isNotEmpty() }
        ?.let { playerConnection.play(it.shuffled()) }

    fun play(index: Int) = playerConnection.play(tracks.value, index)

    fun playNext(track: UnifiedTrack) = playerConnection.playNext(listOf(track))

    fun addToQueue(track: UnifiedTrack) = playerConnection.addToQueue(listOf(track))

    fun startRadio(track: UnifiedTrack) {
        viewModelScope.launch {
            playerConnection.play(listOf(track))
            val radio = musicRepository.generateRadio(track)
            if (radio.isNotEmpty()) playerConnection.addToQueue(radio)
        }
    }

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch { musicRepository.toggleLike(track) }
    }

    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }

    fun canSharePlaylist(): Boolean =
        _playlist.value?.let { shareRepository.canShare(it.source) } ?: false

    fun sharePlaylist() {
        val pl = _playlist.value ?: return
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
