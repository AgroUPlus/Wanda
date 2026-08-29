package com.wander.android.core.playback

import androidx.compose.runtime.Immutable
import com.wander.android.data.model.UnifiedTrack

enum class RepeatMode { OFF, ALL, ONE }

/** Everything the UI needs about playback, in one snapshot. Position is deliberately separate. */
@Immutable
data class PlaybackState(
    val currentTrack: UnifiedTrack? = null,
    val queue: List<UnifiedTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isRadioMode: Boolean = false,
    /**
     * Bumped every time the position jumps rather than advances — a seek, a track boundary.
     *
     * Position is deliberately not in this snapshot (see the note above), and while paused it is
     * the *only* thing a seek changes: same track, same paused, same duration. So the snapshot was
     * identical to the previous one, `distinctUntilChanged` dropped it, and nothing downstream
     * ever learned the playhead had moved. Tapping a lyric while paused jumped the audio and left
     * the highlight where it was.
     *
     * A counter rather than the position itself, because a position in here would recompose every
     * reader twice a second — which is the thing keeping it out was for.
     */
    val seekEpoch: Long = 0L
)

/**
 * Playback rate and pitch.
 *
 * One value, not two, because Media3 sets them together — see
 * [PlayerConnection.setSpeedAndPitch]. Pitch is independent of speed here: slowing a track down
 * without dropping its key is the point of having both.
 */
@Immutable
data class SpeedAndPitch(
    val speed: Float = 1f,
    val pitch: Float = 1f
) {
    val isDefault: Boolean get() = speed == 1f && pitch == 1f

    companion object {
        /**
         * What the player will accept, and therefore what a share link may ask for.
         *
         * Lives here rather than beside the sliders because it is a fact about playback, not about
         * the popup: a link arriving from someone else's phone is checked against the same bounds
         * the local UI offers, in one place, so the two cannot drift apart.
         */
        val RANGE = 0.5f..2.0f
    }
}

@Immutable
data class PlaybackPosition(
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L
)
