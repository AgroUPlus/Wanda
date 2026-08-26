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
    val isRadioMode: Boolean = false
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
}

@Immutable
data class PlaybackPosition(
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L
)
