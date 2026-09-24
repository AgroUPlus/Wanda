package com.wander.android.core.playback

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates jam sessions, room proposals, and listen-along following on [PlayerConnection].
 */
internal class PlayerJamCoordinator(
    private val scope: CoroutineScope,
    private val orderLocked: MutableStateFlow<Boolean>,
    private val queueManager: PlayerQueueManager
) {
    var isFollowing: Boolean = false
        private set

    private var onLeaveFollowing: (() -> Unit)? = null
    var onPlayInJam: ((List<UnifiedTrack>, Int) -> Unit)? = null
        private set

    val isInJam: Boolean get() = onPlayInJam != null

    fun setFollowing(following: Boolean, onLeave: (() -> Unit)? = null) {
        isFollowing = following
        onLeaveFollowing = if (following) onLeave else null
        orderLocked.value = isFollowing || isInJam
    }

    fun onUserInitiatedPlay() {
        if (isFollowing) onLeaveFollowing?.invoke()
    }

    fun setJamProposal(
        controller: MediaController?,
        propose: ((List<UnifiedTrack>, Int) -> Unit)?
    ) {
        onPlayInJam = propose
        orderLocked.value = isFollowing || isInJam
        if (propose == null) return
        scope.launch {
            controller?.let { ctrl ->
                ctrl.repeatMode = Player.REPEAT_MODE_OFF
                ctrl.shuffleModeEnabled = false
            }
        }
    }

    fun playForJam(ctrl: MediaController?, tracks: List<UnifiedTrack>, startPositionMs: Long = 0L) {
        ctrl?.let { queueManager.play(it, tracks, 0, startPositionMs) }
    }

    fun followerSeek(ctrl: MediaController?, positionMs: Long) {
        ctrl?.seekTo(positionMs)
    }

    fun followerSetPlaying(ctrl: MediaController?, shouldPlay: Boolean, isLive: Boolean) {
        if (ctrl == null) return
        if (ctrl.playWhenReady != shouldPlay) {
            if (shouldPlay) {
                if (isLive) ctrl.seekToDefaultPosition()
                ctrl.play()
            } else {
                ctrl.pause()
            }
        }
    }
}
