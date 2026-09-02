package com.wander.android

import com.wander.android.core.audio.fingerprint.OffsetAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alignment vote, and the phase split it exists to survive.
 *
 * Worth pinning as a pure function: the failure it addresses is not a crash but a confidently wrong
 * answer, which no amount of holding a phone at a speaker reliably reproduces.
 */
class OffsetAlignmentTest {

    @Test
    fun `nothing to align on is no answer`() {
        assertNull(OffsetAlignment.best(emptyMap()))
    }

    @Test
    fun `a clean alignment is reported at its own offset`() {
        val aligned = OffsetAlignment.best(mapOf(40 to 30, 900 to 2))!!
        assertEquals(40, aligned.offsetFrames)
        assertEquals(30, aligned.votes)
    }

    /**
     * The case the tolerance exists for. One true alignment, split by microphone phase across two
     * adjacent frames, must read as its full strength rather than as half of it.
     */
    @Test
    fun `a split alignment is counted whole`() {
        val split = mapOf(40 to 16, 41 to 14)
        val aligned = OffsetAlignment.best(split)!!
        assertEquals(30, aligned.votes)
        assertTrue("the offset must be one of the two halves", aligned.offsetFrames in listOf(40, 41))
    }

    /**
     * The failure that split votes actually produce: a real match diluted below a coincidence.
     * Counted whole, the real one wins; counted per-bin it would not.
     */
    @Test
    fun `a split true match still beats an unsplit coincidence`() {
        val real = OffsetAlignment.best(mapOf(40 to 11, 41 to 10))!!
        val coincidence = OffsetAlignment.best(mapOf(700 to 13))!!
        assertTrue(
            "21 real votes must outrank 13 coincidental ones, not lose 11 to 13",
            real.votes > coincidence.votes
        )
    }

    /** Two frames apart is past what phase can explain, and must not be gathered into one score. */
    @Test
    fun `alignments two frames apart are not merged`() {
        val aligned = OffsetAlignment.best(mapOf(40 to 10, 43 to 10))!!
        assertEquals("distant bins must not sum", 10, aligned.votes)
    }

    /** Negative offsets are ordinary: the clip can start before the landmark it matched. */
    @Test
    fun `negative offsets align like any other`() {
        val aligned = OffsetAlignment.best(mapOf(-5 to 9, -4 to 7))!!
        assertEquals(16, aligned.votes)
    }
}
