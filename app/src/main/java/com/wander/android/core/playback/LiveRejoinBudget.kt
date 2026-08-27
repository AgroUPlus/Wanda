package com.wander.android.core.playback

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * How often a livestream may be put back on the air before the failure is reported instead.
 *
 * The budget is per burst, not per lifetime. Two very different things look identical from a
 * single failure: a stream that has gone off the air, which fails again the instant it is
 * re-prepared, and a stream that is perfectly healthy on a phone that went through a tunnel. Only
 * the *spacing* tells them apart, so a run of failures close together exhausts the budget and
 * surfaces, while an isolated one hours into a listen is free.
 *
 * A plain lifetime counter got this wrong in the way that matters for radio: three dropouts over
 * an afternoon and the stream could never recover again.
 */
internal class LiveRejoinBudget(
    private val maxPerBurst: Int = 3,
    private val burstWindowMs: Long = 60_000L,
    private val now: () -> Long = SystemClock::elapsedRealtime
) {
    private data class Burst(val attempts: Int, val lastAt: Long)

    private val bursts = ConcurrentHashMap<String, Burst>()

    /** Records an attempt for [id] and says whether it is within budget. */
    fun allow(id: String): Boolean {
        val at = now()
        val burst = bursts.compute(id) { _, previous ->
            // Far enough from the last failure to be a new incident rather than a retry loop.
            if (previous == null || at - previous.lastAt > burstWindowMs) {
                Burst(attempts = 1, lastAt = at)
            } else {
                Burst(attempts = previous.attempts + 1, lastAt = at)
            }
        }
        return (burst?.attempts ?: 1) <= maxPerBurst
    }
}
