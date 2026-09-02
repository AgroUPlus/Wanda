package com.wander.android

import com.wander.android.core.audio.fingerprint.MatchConfidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accept/refuse rule, against numbers measured on a real library.
 *
 * Every case below is a logged attempt from a phone held at a speaker with 108 tracks indexed. They
 * are here because the previous rule — a ratio against the runner-up — refused the one correct
 * identification among them while being unable to explain why the three wrong ones were wrong.
 */
class MatchConfidenceTest {

    /**
     * The identification that was thrown away: hanbee's "Buttercup", 214 votes against a runner-up
     * of 144. A ratio of 1.49 failed a 1.6 margin, yet the lead over the noise is enormous.
     */
    @Test
    fun `the measured true match is accepted`() {
        val scores = noise(105) + listOf(214, 144)
        val verdict = MatchConfidence.assess(scores)
        assertTrue(
            "214 over a floor near 110 is an identification, not a coin toss",
            verdict.accepted
        )
    }

    /** The three measured misses, each of which the old rule also refused — for the wrong reason. */
    @Test
    fun `the measured misses are refused`() {
        for ((best, runnerUp) in listOf(142 to 140, 158 to 148, 110 to 109)) {
            val verdict = MatchConfidence.assess(noise(105) + listOf(best, runnerUp))
            assertFalse("$best against $runnerUp is noise, not an answer", verdict.accepted)
        }
    }

    /** A clip of a quiet room: everything scores alike and nothing stands out. */
    @Test
    fun `a flat distribution names nobody`() {
        assertFalse(MatchConfidence.assess(List(50) { 40 }).accepted)
    }

    /**
     * A small index has no crowd for a coincidence to be the luckiest of, so the floor is taken as
     * zero and a clear winner still wins.
     */
    @Test
    fun `a lone strong candidate is accepted`() {
        assertTrue(MatchConfidence.assess(listOf(90)).accepted)
        assertTrue(MatchConfidence.assess(listOf(90, 20)).accepted)
    }

    @Test
    fun `nothing at all is not an answer`() {
        assertFalse(MatchConfidence.assess(emptyList()).accepted)
    }

    /** The band every candidate sat in across all four attempts, hit and miss alike. */
    private fun noise(count: Int): List<Int> = List(count) { 90 + (it % 40) }
}
