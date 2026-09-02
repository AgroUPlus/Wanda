package com.wander.android

import com.wander.android.core.audio.melody.OctaveCorrection
import com.wander.android.core.audio.melody.PitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Folding octave slips out of a pitch track.
 *
 * Measured on a real library, a quarter of every stored interval was a jump of eleven semitones or
 * more — the detector doubling or halving a period rather than a tune moving. The cases below are
 * that shape, plus the ones the fold must not damage.
 */
class OctaveCorrectionTest {

    private fun hz(midi: Float) = 440f * Math.pow(2.0, ((midi - 69f) / 12f).toDouble()).toFloat()
    private fun midi(hz: Float) = PitchDetector.midiOf(hz)

    /** A steady note with one frame doubled comes back steady. */
    @Test
    fun `a single slipped frame is folded back`() {
        val track = FloatArray(20) { hz(60f) }
        track[12] = hz(72f)
        val fixed = OctaveCorrection.apply(track)
        assertTrue(
            "the slipped frame should return to the note, was ${midi(fixed[12])}",
            abs(midi(fixed[12]) - 60f) < 0.01f
        )
    }

    /** A run of slipped frames is folded too — a slip often lasts a phrase, not a frame. */
    @Test
    fun `a run of slipped frames is folded`() {
        val track = FloatArray(30) { if (it in 10..18) hz(48f) else hz(60f) }
        val fixed = OctaveCorrection.apply(track)
        for (i in 10..18) {
            assertTrue("frame $i still an octave out: ${midi(fixed[i])}", abs(midi(fixed[i]) - 60f) < 0.01f)
        }
    }

    /** A tune that moves by ordinary intervals is not touched. */
    @Test
    fun `an ordinary melody passes through unchanged`() {
        val tune = floatArrayOf(60f, 62f, 64f, 65f, 67f, 65f, 64f, 62f, 60f, 59f, 60f, 62f)
        val track = FloatArray(tune.size) { hz(tune[it]) }
        val fixed = OctaveCorrection.apply(track)
        for (i in tune.indices) {
            assertEquals("note $i moved", tune[i], midi(fixed[i]), 0.01f)
        }
    }

    /** Silence is where a note ends, and must stay silence. */
    @Test
    fun `unvoiced frames are left alone`() {
        val track = floatArrayOf(hz(60f), 0f, 0f, hz(60f), 0f)
        val fixed = OctaveCorrection.apply(track)
        assertEquals(0f, fixed[1], 0f)
        assertEquals(0f, fixed[2], 0f)
        assertEquals(0f, fixed[4], 0f)
    }

    /** Nothing to reference yet: the first voiced frame sets the reference rather than moving. */
    @Test
    fun `the first frame is taken as given`() {
        val track = floatArrayOf(hz(72f), hz(72f), hz(72f))
        val fixed = OctaveCorrection.apply(track)
        assertEquals(72f, midi(fixed[0]), 0.01f)
    }

    @Test
    fun `an empty track is not an error`() {
        assertEquals(0, OctaveCorrection.apply(FloatArray(0)).size)
    }

    /**
     * The shape actually seen in the stored data: a flat line with one leap out and straight back.
     * Both halves must go, because it is the *pair* that wrecks the phrase around it.
     */
    @Test
    fun `the out-and-back slip seen in real contours is removed`() {
        val track = FloatArray(24) { hz(57f) }
        track[14] = hz(74f)
        track[15] = hz(74f)
        val fixed = OctaveCorrection.apply(track)
        val intervals = (1 until fixed.size).map { midi(fixed[it]) - midi(fixed[it - 1]) }
        assertTrue(
            "no interval should exceed a fifth after folding: ${intervals.map { it.toInt() }}",
            intervals.all { abs(it) <= 7f }
        )
    }
}
