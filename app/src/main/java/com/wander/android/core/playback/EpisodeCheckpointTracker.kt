package com.wander.android.core.playback

import androidx.media3.common.C
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One reading of how far into [trackId] the listener had got. */
data class EpisodeCheckpoint(val trackId: String, val positionMs: Long, val durationMs: Long)

/**
 * Tracks episode playback positions and emits checkpoints when leaving or pausing on an episode.
 */
internal class EpisodeCheckpointTracker {

    private val _episodeCheckpoints = MutableSharedFlow<EpisodeCheckpoint>(extraBufferCapacity = 8)
    val episodeCheckpoints: SharedFlow<EpisodeCheckpoint> = _episodeCheckpoints.asSharedFlow()

    private var lastKnownDuration: Pair<String, Long>? = null

    fun rememberDuration(track: UnifiedTrack?, durationMs: Long) {
        if (track == null || durationMs == C.TIME_UNSET || durationMs <= 0L) return
        lastKnownDuration = track.id to durationMs
    }

    fun checkpoint(track: UnifiedTrack?, positionMs: Long, durationMs: Long) {
        if (track?.isEpisode != true || positionMs <= 0L) return
        val remembered = lastKnownDuration?.takeIf { it.first == track.id }?.second
        _episodeCheckpoints.tryEmit(
            EpisodeCheckpoint(
                trackId = track.id,
                positionMs = positionMs,
                durationMs = remembered
                    ?: durationMs.takeIf { it > 0L && it != C.TIME_UNSET }
                    ?: track.durationMs
            )
        )
    }
}
