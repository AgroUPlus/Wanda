package com.wander.android.core.playback

/**
 * Relative jumps for spoken audio, which replace previous/next while an episode plays.
 *
 * Asymmetric on purpose, as in every podcast app: back is for a missed sentence, forward is for a
 * sponsor read.
 */
object EpisodeJumps {
    const val BACK_MS = 10_000L
    const val FORWARD_MS = 30_000L

    /**
     * Where a jump of [deltaMs] from [positionMs] lands. Clamped to the episode, and only at the
     * start when [durationMs] is not known yet, since overshooting an unknown end reads as a skip.
     */
    fun target(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val raw = (positionMs + deltaMs).coerceAtLeast(0L)
        return if (durationMs > 0L) raw.coerceAtMost(durationMs) else raw
    }
}
