package com.wander.android.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlaybackCoordinator
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val coordinator: PlaybackCoordinator,
    private val musicRepository: MusicRepository,
    private val shareRepository: ShareRepository
) : ViewModel() {

    val lyrics = coordinator.lyrics

    /**
     * The playing track is a snapshot taken when it was queued, so its own `isLiked` never changes
     * while it plays. The heart reads Room instead.
     */
    val likedTrackIds: StateFlow<Set<String>> = musicRepository.getLikedTrackIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleLike(track: UnifiedTrack) {
        viewModelScope.launch {
            musicRepository.toggleLike(track)
        }
    }

    /** Whether this track's backend can mint a public link — decides if the button is shown. */
    fun canShare(track: UnifiedTrack) = shareRepository.canShare(track)

    /** The link is published on a shared flow and raised as a share sheet by `WanderApp`. */
    fun share(track: UnifiedTrack) {
        viewModelScope.launch { shareRepository.share(track) }
    }
}
