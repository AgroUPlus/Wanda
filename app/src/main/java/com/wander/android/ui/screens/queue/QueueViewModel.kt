package com.wander.android.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository
) : ViewModel() {

    fun playNext(track: UnifiedTrack) {
        playerConnection.playNext(listOf(track))
    }

    fun addToQueue(track: UnifiedTrack) {
        playerConnection.addToQueue(listOf(track))
    }

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

    fun canShare(track: UnifiedTrack): Boolean = shareRepository.canShare(track)

    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }

    fun removeFromQueue(index: Int) {
        playerConnection.removeFromQueue(index)
    }
}
