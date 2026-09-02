package com.wander.android

import com.wander.android.core.notification.WorkEta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The estimate on the work notification.
 *
 * Worth pinning because every one of its failures is a lie told confidently to someone deciding
 * whether to leave their phone alone for the evening, and none of them looks like a crash. The
 * silences are as much the contract as the phrases: an estimate that appears too early, or that
 * survives past the end of the job, is worse than no estimate.
 */
class WorkEtaTest {

    private val start = 1_000_000L

    private fun eta() = WorkEta(start)

    /**
     * One sample is not an average.
     *
     * On a job that alternates between local files and streamed audio the first item is routinely
     * off by an order of magnitude, and an estimate that opens with "4 hours left" and settles to
     * six minutes is read once, believed, and the job abandoned.
     */
    @Test
    fun `nothing is offered before there are enough samples`() {
        assertNull(eta().describe(done = 0, total = 100, nowMs = start + 10_000))
        assertNull(eta().describe(done = 1, total = 100, nowMs = start + 10_000))
        assertNull(eta().describe(done = 2, total = 100, nowMs = start + 10_000))
        assertNotNull(eta().describe(done = 3, total = 100, nowMs = start + 10_000))
    }

    /** There is no time left on a job that is over, and no rounding that makes "0 min" useful. */
    @Test
    fun `a finished job has no estimate`() {
        assertNull(eta().describe(done = 100, total = 100, nowMs = start + 60_000))
        // Past the end — a batch that grew shorter mid-run, say — is the same answer.
        assertNull(eta().describe(done = 101, total = 100, nowMs = start + 60_000))
    }

    /**
     * The degenerate inputs, which are the ones that would divide by zero or read as sarcasm.
     *
     * `total = 0` is reachable: a run whose candidate list empties between the count and the
     * notification.
     */
    @Test
    fun `an empty job and a clock that has not moved say nothing`() {
        assertNull(eta().describe(done = 0, total = 0, nowMs = start + 1_000))
        assertNull(eta().describe(done = 5, total = 100, nowMs = start))
        // A clock that went backwards — NTP correcting mid-run — is not an estimate either.
        assertNull(eta().describe(done = 5, total = 100, nowMs = start - 1_000))
    }

    /** Ten items in ten seconds, ninety left: ninety seconds, which reads as a minute and a half. */
    @Test
    fun `the estimate extrapolates from what has been measured`() {
        assertEquals("about 1 min left", eta().describe(done = 10, total = 100, nowMs = start + 10_000))
    }

    /** Under a minute is said in words, because "about 0 min left" is not an answer. */
    @Test
    fun `less than a minute is named rather than rounded to zero`() {
        assertEquals("under a minute left", eta().describe(done = 90, total = 100, nowMs = start + 90_000))
    }

    /** An hour and above gains the hour, and drops the minutes when there are none. */
    @Test
    fun `hours are spelled out`() {
        // 10 done in 10 minutes, 90 left -> 90 minutes.
        assertEquals(
            "about 1 h 30 min left",
            eta().describe(done = 10, total = 100, nowMs = start + 600_000)
        )
        // 10 done in 20 minutes, 90 left -> 180 minutes, exactly three hours.
        assertEquals(
            "about 3 h left",
            eta().describe(done = 10, total = 100, nowMs = start + 1_200_000)
        )
    }

    /**
     * The estimate is a running average, not a fixed one.
     *
     * The same job slowing down — a local batch giving way to streamed tracks — must be reflected,
     * because that transition is exactly when the number stops being trustworthy otherwise.
     */
    @Test
    fun `a job that slows down reports more time left than it did`() {
        val eta = eta()
        val early = eta.describe(done = 10, total = 100, nowMs = start + 10_000)
        val later = eta.describe(done = 20, total = 100, nowMs = start + 120_000)
        assertEquals("about 1 min left", early)
        assertEquals("about 8 min left", later)
    }
}
