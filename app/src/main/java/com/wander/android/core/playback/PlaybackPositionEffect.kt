package com.wander.android.core.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * The playback position, sampled only while something is playing **and** the composable is on
 * screen. The previous implementation polled every 200 ms forever, awake or not; this is the
 * single largest battery win in the app.
 *
 * @param intervalMs sampling interval. 500 ms is imperceptible on a progress bar and halves the
 *   wakeups of the old 200 ms loop.
 */
@Composable
fun rememberPlaybackPosition(
    connection: PlayerConnection,
    intervalMs: Long = 500L
): State<PlaybackPosition> {
    val controller by connection.controller.collectAsStateWithLifecycle()
    val state by connection.state.collectAsStateWithLifecycle()

    return produceState(
        initialValue = PlaybackPosition(),
        controller,
        state.isPlaying,
        state.currentTrack?.id
    ) {
        val ctrl = controller ?: run {
            value = PlaybackPosition()
            return@produceState
        }
        // Emit once even while paused, so a seek or track change shows up immediately.
        value = PlaybackPosition(ctrl.currentPosition, ctrl.bufferedPosition)
        while (state.isPlaying) {
            delay(intervalMs)
            value = PlaybackPosition(ctrl.currentPosition, ctrl.bufferedPosition)
        }
    }
}

/** Progress in 0f..1f, guarding the unknown-duration case rather than dividing by zero. */
fun progressOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
