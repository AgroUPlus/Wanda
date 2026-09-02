package com.wander.android

import com.wander.android.core.audio.fingerprint.MatchConfidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the narrowing shortlist is allowed to claim while the microphone is still open.
 *
 * The animation's honesty rests on these: a partial clip must not present itself as a verdict, and
 * the bar a row draws must be the quantity the verdict is actually taken on.
 */
class RecognitionProgressTest {

    /**
     * Two seconds in, everything scores alike because there is barely any audio to align. Nothing
     * may be declared at that point — a confident answer from a flat distribution is exactly the
     * fake deliberation this path exists to avoid.
     */
    @Test
    fun `an early flat distribution is not settled`() {
        val early = MatchConfidence.assess(List(80) { 30 + (it % 6) })
        assertFalse("a clip with no separation names nobody", early.accepted)
    }

    /** By the end the leader has pulled clear of the same crowd, and only then is it an answer. */
    @Test
    fun `a separated distribution is accepted`() {
        val late = MatchConfidence.assess(List(80) { 90 + (it % 40) } + listOf(240))
        assertTrue("a clear lead over the noise is an identification", late.accepted)
    }

    /**
     * The bar is drawn from the lead, not the votes. With a noise floor near 110, two candidates
     * at 214 and 144 are a landslide; drawn from raw votes they would look nearly equal, which is
     * the misreading the whole `MatchConfidence` change exists to correct.
     */
    @Test
    fun `the lead separates candidates that raw votes do not`() {
        val verdict = MatchConfidence.assess(List(105) { 90 + (it % 40) } + listOf(214, 144))
        val leader = 214 - verdict.noiseFloor
        val runnerUp = 144 - verdict.noiseFloor

        assertTrue("raw votes barely separate them", 144f / 214f > 0.6f)
        assertTrue("the lead separates them plainly", runnerUp.toFloat() / leader < 0.5f)
    }
}
