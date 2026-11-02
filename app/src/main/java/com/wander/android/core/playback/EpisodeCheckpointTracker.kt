package com.wander.android.core.playback

import androidx.media3.common.C
import com.wander.android.data.model.UnifiedTrack

/** One reading of how far into [trackId] the listener had got. */
data class EpisodeCheckpoint(val trackId: String, val positionMs: Long, val durationMs: Long)

/**
 * Turns a reading of the player into an episode checkpoint worth saving, remembering each
 * episode's duration as the player reports it. See [EpisodeProgressRecorder] for when it is read.
 */
internal class EpisodeCheckpointTracker {

    private var lastKnownDuration: Pair<String, Long>? = null

    fun rememberDuration(track: UnifiedTrack?, durationMs: Long) {
        if (track == null || durationMs == C.TIME_UNSET || durationMs <= 0L) return
        lastKnownDuration = track.id to durationMs
    }

    /** What to save for [track] at [positionMs], or null when it is no episode or not started. */
    fun checkpoint(track: UnifiedTrack?, positionMs: Long, durationMs: Long): EpisodeCheckpoint? {
        if (track?.isEpisode != true || positionMs <= 0L) return null
        val remembered = lastKnownDuration?.takeIf { it.first == track.id }?.second
        return EpisodeCheckpoint(
            trackId = track.id,
            positionMs = positionMs,
            durationMs = remembered
                ?: durationMs.takeIf { it > 0L && it != C.TIME_UNSET }
                ?: track.durationMs
        )
    }
}
