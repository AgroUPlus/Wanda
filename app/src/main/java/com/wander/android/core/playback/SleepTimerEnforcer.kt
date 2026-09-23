package com.wander.android.core.playback

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Carries out [SleepTimer] on the service's player.
 *
 * A countdown fades out over [FADE_MS] before pausing: stopping dead is what wakes someone who
 * had just fallen asleep. "End of item" uses Media3's own `pauseAtEndOfMediaItems`, which stops
 * exactly on the boundary rather than a poll's worth after it.
 *
 * Collected with `collectLatest`, so changing or cancelling the timer abandons a fade in progress —
 * and the `finally` puts the volume back, whichever way the fade ended.
 */
internal class SleepTimerEnforcer(
    private val player: ExoPlayer,
    private val timer: SleepTimer,
    scope: CoroutineScope
) : Player.Listener {

    init {
        player.addListener(this)
        scope.launch {
            timer.state.collectLatest { state ->
                player.pauseAtEndOfMediaItems = state == SleepTimerState.EndOfItem
                if (state is SleepTimerState.Countdown) countDown(state)
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
            timer.state.value == SleepTimerState.EndOfItem
        ) {
            timer.cancel()
        }
    }

    private suspend fun countDown(state: SleepTimerState.Countdown) {
        delay((state.endsAtElapsedMs - SystemClock.elapsedRealtime() - FADE_MS).coerceAtLeast(0L))
        val volume = player.volume
        try {
            repeat(FADE_STEPS) { step ->
                player.volume = volume * (1f - (step + 1f) / FADE_STEPS)
                delay(FADE_MS / FADE_STEPS)
            }
            player.pause()
            timer.cancel()
        } finally {
            player.volume = volume
        }
    }

    private companion object {
        const val FADE_MS = 3_000L
        const val FADE_STEPS = 30
    }
}
