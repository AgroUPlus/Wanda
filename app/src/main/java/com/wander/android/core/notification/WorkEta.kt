package com.wander.android.core.notification

import android.content.res.Resources
import com.wander.android.R
import kotlin.time.Duration.Companion.milliseconds

/**
 * How much longer a per-item job has to run, from how long it has taken so far.
 *
 * Measured, never assumed. The cost of measuring one track varies by more than an order of
 * magnitude — a local FLAC is a disk read, a streamed track is a network fetch of about a minute of
 * audio — so any constant baked in here would be wrong for most libraries. The running average is
 * the only estimate that adapts to the collection it is actually looking at.
 *
 * ## Why it stays quiet at the start
 *
 * The first item's timing is not an average, it is one sample, and on a job that alternates between
 * cached and uncached sources the first sample is routinely off by 10x. An estimate that opens with
 * "4 hours left" and settles to six minutes is worse than no estimate — it is read once, believed,
 * and the job is abandoned. So nothing is offered until [MIN_SAMPLES] items have completed.
 */
internal class WorkEta(private val startedAtMs: Long) {

    /** [estimate], as the phrase the notification shows; null when it is too early to say. */
    fun describe(resources: Resources, done: Int, total: Int, nowMs: Long): String? =
        when (val left = estimate(done, total, nowMs)) {
            null -> null
            Estimate.UnderAMinute -> resources.getString(R.string.work_eta_under_minute)
            is Estimate.Minutes -> resources.getQuantityString(R.plurals.work_eta_minutes, left.minutes, left.minutes)
            is Estimate.Hours -> if (left.minutes == 0) {
                resources.getQuantityString(R.plurals.work_eta_hours, left.hours, left.hours)
            } else {
                resources.getString(R.string.work_eta_hours_minutes, left.hours, left.minutes)
            }
        }

    /**
     * How long is left after [done] of [total], or null when it is too early to say.
     *
     * Deliberately coarse. Rounding to the minute above ten minutes says "this is an estimate" in a
     * way that "17 min 42 s" does not, and the extra precision is spurious — it is an average over
     * items whose real cost varies wildly.
     */
    fun estimate(done: Int, total: Int, nowMs: Long): Estimate? {
        if (done < MIN_SAMPLES || done >= total) return null

        val elapsed = nowMs - startedAtMs
        if (elapsed <= 0L) return null

        val remaining = ((total - done).toDouble() * elapsed / done).toLong().milliseconds
        val minutes = remaining.inWholeMinutes

        return when {
            minutes < 1L -> Estimate.UnderAMinute
            minutes < MINUTES_PER_HOUR -> Estimate.Minutes(minutes.toInt())
            else -> {
                val hours = remaining.inWholeHours
                Estimate.Hours(hours.toInt(), (minutes - hours * MINUTES_PER_HOUR).toInt())
            }
        }
    }

    /** The time left, in the units the notification speaks in. */
    sealed interface Estimate {
        data object UnderAMinute : Estimate
        data class Minutes(val minutes: Int) : Estimate
        /** [minutes] is what is left over after the whole hours, 0 to 59. */
        data class Hours(val hours: Int, val minutes: Int) : Estimate
    }

    private companion object {
        /** Enough completed items for the average to mean something rather than echo the first. */
        const val MIN_SAMPLES = 3
        const val MINUTES_PER_HOUR = 60L
    }
}
