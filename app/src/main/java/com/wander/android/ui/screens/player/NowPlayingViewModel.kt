package com.wander.android.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val coordinator: PlaybackCoordinator,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val lyrics = coordinator.lyrics

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch {
            musicRepository.toggleLike(track)
        }
    }
}
