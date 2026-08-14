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

@Immutable
data class PlaybackPosition(
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L
)
