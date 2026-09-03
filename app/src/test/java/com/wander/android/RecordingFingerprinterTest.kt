package com.wander.android

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.fingerprint.RecordingFingerprinter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the canonical fingerprint has to survive, and what it has to tell apart.
 *
 * The whole feature rests on one claim: two encodings of the same recording produce nearly the
 * same fingerprint, and two different recordings do not. These are that claim.
 */
class RecordingFingerprinterTest {

    private val seconds = 6
    private val sampleCount = AudioFormat.SAMPLE_RATE * seconds

    /**
     * Something broadband, because real music is.
     *
     * An earlier version of this used six partials below 1 kHz, which left two thirds of the
     * bands holding nothing but whatever noise a test added — so their bits were random and the
     * fingerprint looked far more fragile than it is. A signal that occupies the band range is
     * what the fingerprinter is actually asked to describe.
     */
    private fun music(seed: Int): FloatArray {
        val random = Random(seed)
        val partials = List(24) { 120.0 * (it + 1) + random.nextDouble(-25.0, 25.0) }
        val phases = partials.map { random.nextDouble(0.0, 2 * PI) }
        return FloatArray(sampleCount) { n ->
            val t = n.toDouble() / AudioFormat.SAMPLE_RATE
            // A slow envelope, so band energies move over time and the bits carry information.
            val envelope = 0.5 + 0.5 * sin(2 * PI * 0.7 * t + seed)
            partials.mapIndexed { index, hz ->
                envelope * sin(2 * PI * hz * t + phases[index]) / (index + 1)
            }.sum().toFloat() * 0.2f
        }
    }

    private fun noisy(source: FloatArray, amount: Float, seed: Int): FloatArray {
        val random = Random(seed)
        return FloatArray(source.size) { source[it] + random.nextDouble(-1.0, 1.0).toFloat() * amount }
    }

    @Test
    fun `the same audio fingerprints identically`() {
        val audio = music(1)
        assertTrue(
            RecordingFingerprinter.fingerprint(audio)
                .contentEquals(RecordingFingerprinter.fingerprint(audio))
        )
    }

    /** One per frame, less the span each bit reaches back across. */
    @Test
    fun `a fingerprint has one sub-hash per frame the time delta can reach`() {
        val audio = music(1)
        val frames = (audio.size - AudioFormat.FRAME_SIZE) / AudioFormat.HOP_SIZE + 1
        assertEquals(frames - RecordingFingerprinter.TIME_DELTA_FRAMES, RecordingFingerprinter.fingerprint(audio).size)
    }

    /** The property the whole catalogue depends on: volume is not identity. */
    @Test
    fun `a gain change leaves the fingerprint alone`() {
        val audio = music(2)
        val quieter = FloatArray(audio.size) { audio[it] * 0.35f }

        val similarity = RecordingFingerprinter.similarity(
            RecordingFingerprinter.fingerprint(audio),
            RecordingFingerprinter.fingerprint(quieter)
        )
        assertTrue("gain changed the fingerprint: $similarity", similarity > 0.99)
    }

    /**
     * Added noise degrades the fingerprint, and it has to stay clear of chance while it does.
     *
     * The absolute number here is low on purpose — measured, not hoped for. One bit is one
     * comparison of two nearly-equal energies, so noise flips the bits sitting near that
     * boundary, and a fingerprint of degraded audio lands well below a perfect match. What
     * matters is that it stays well above the ~0.5 two unrelated recordings score, which is what
     * the next test pins down.
     */
    @Test
    fun `noisy audio still resembles what it came from`() {
        val audio = music(3)
        val similarity = RecordingFingerprinter.similarity(
            RecordingFingerprinter.fingerprint(audio),
            RecordingFingerprinter.fingerprint(noisy(audio, 0.002f, 9))
        )
        assertTrue("noise destroyed the fingerprint: $similarity", similarity > 0.60)
    }

    /** The margin the matcher has to work in: degraded-same must beat unrelated, clearly. */
    @Test
    fun `a degraded copy scores well above an unrelated recording`() {
        val audio = music(3)
        val degraded = RecordingFingerprinter.similarity(
            RecordingFingerprinter.fingerprint(audio),
            RecordingFingerprinter.fingerprint(noisy(audio, 0.002f, 9))
        )
        val unrelated = RecordingFingerprinter.similarity(
            RecordingFingerprinter.fingerprint(audio),
            RecordingFingerprinter.fingerprint(music(11))
        )
        assertTrue("no margin: $degraded vs $unrelated", degraded - unrelated > 0.10)
    }

    /**
     * Exact sub-hash equality is a re-encode test, not a degradation test.
     *
     * A single flipped bit changes the whole 32-bit value, so an index keyed on exact sub-hashes
     * finds a file that was re-containered or re-levelled and finds nothing at all once the audio
     * has been through a lossy encoder. That is a real constraint on how the catalogue looks
     * things up, and it is recorded here rather than discovered later.
     */
    @Test
    fun `exact sub-hashes survive a gain change but not added noise`() {
        val audio = music(12)
        val original = RecordingFingerprinter.fingerprint(audio).toSet()
        val quieter = RecordingFingerprinter
            .fingerprint(FloatArray(audio.size) { audio[it] * 0.35f }).toSet()
        val noised = RecordingFingerprinter.fingerprint(noisy(audio, 0.002f, 3)).toSet()

        assertTrue(
            "a gain change should keep most sub-hashes",
            original.intersect(quieter).size > original.size * 0.8
        )
        assertTrue(
            "noise is not expected to preserve exact sub-hashes",
            original.intersect(noised).size < original.size * 0.1
        )
    }

    /** And the other half: it must not call everything the same recording. */
    @Test
    fun `different recordings do not look alike`() {
        val similarity = RecordingFingerprinter.similarity(
            RecordingFingerprinter.fingerprint(music(4)),
            RecordingFingerprinter.fingerprint(music(5))
        )
        assertTrue("unrelated audio matched: $similarity", similarity < 0.55)
    }

    @Test
    fun `silence and a tone are separated by more than loudness`() {
        val silence = FloatArray(sampleCount)
        val similarity = RecordingFingerprinter.similarity(
            RecordingFingerprinter.fingerprint(silence),
            RecordingFingerprinter.fingerprint(music(6))
        )
        assertTrue("silence matched music: $similarity", similarity < 0.9)
    }

    @Test
    fun `audio shorter than a frame produces nothing rather than throwing`() {
        assertEquals(0, RecordingFingerprinter.fingerprint(FloatArray(100)).size)
    }

    @Test
    fun `a fingerprint compared with itself is a perfect match`() {
        val print = RecordingFingerprinter.fingerprint(music(7))
        assertEquals(1.0, RecordingFingerprinter.similarity(print, print), 1e-9)
    }

    // -- alignment ---------------------------------------------------------
    //
    // Two uploads of one song rarely start at the same instant: intros, countdowns and trimmed
    // silence shift one against the other. Compared from index 0 such a pair scores at chance and
    // is read as two different recordings. Measured across a real 1,355-track library, aligning
    // took same-title matches from 32 of 44 to 43 of 44, with no false positive among 2,415
    // unrelated pairs.

    /** Drops [frames] worth of samples from the front, as a longer intro would. */
    private fun shifted(source: FloatArray, frames: Int): FloatArray {
        val offset = frames * AudioFormat.HOP_SIZE
        return source.copyOfRange(offset.coerceAtMost(source.size), source.size)
    }

    @Test
    fun `a shifted copy is recognised and its offset reported`() {
        val original = RecordingFingerprinter.fingerprint(music(11))
        val late = RecordingFingerprinter.fingerprint(shifted(music(11), frames = 40))

        val alignment = RecordingFingerprinter.aligned(original, late)
        assertEquals("offset should be the shift applied", 40, alignment.offsetFrames)
        assertTrue(
            "a shifted copy should still score high, was ${alignment.similarity}",
            alignment.similarity > 0.9
        )
    }

    @Test
    fun `a one frame shift does not sink a real match`() {
        // The failure that motivated this: four pairs in a real library sat at 0.69-0.71 -- just
        // under the 0.72 threshold -- purely because they were one 32 ms frame out.
        val original = RecordingFingerprinter.fingerprint(music(5))
        val nudged = RecordingFingerprinter.fingerprint(shifted(music(5), frames = 1))

        assertTrue(
            "one frame out must not read as a different recording",
            RecordingFingerprinter.similarity(original, nudged) > 0.9
        )
    }

    @Test
    fun `alignment does not invent a match between different recordings`() {
        // The point of aligning is to find a shift that exists, never to search until something
        // scores well. Unrelated audio must stay at chance however it is slid.
        val one = RecordingFingerprinter.fingerprint(music(1))
        val other = RecordingFingerprinter.fingerprint(music(2))

        val alignment = RecordingFingerprinter.aligned(one, other)
        assertTrue(
            "unrelated recordings should stay near chance, was ${alignment.similarity}",
            alignment.similarity < 0.7
        )
    }

    @Test
    fun `identical fingerprints align at zero`() {
        val fingerprint = RecordingFingerprinter.fingerprint(music(3))
        val alignment = RecordingFingerprinter.aligned(fingerprint, fingerprint)

        assertEquals(0, alignment.offsetFrames)
        assertEquals(1.0, alignment.similarity, 1e-9)
    }

    @Test
    fun `an empty fingerprint aligns with nothing`() {
        val fingerprint = RecordingFingerprinter.fingerprint(music(4))

        assertEquals(0.0, RecordingFingerprinter.similarity(fingerprint, IntArray(0)), 1e-9)
        assertEquals(0.0, RecordingFingerprinter.similarity(IntArray(0), fingerprint), 1e-9)
        assertEquals(0.0, RecordingFingerprinter.aligned(IntArray(0), IntArray(0)).similarity, 1e-9)
    }

    @Test
    fun `the reported offset never leaves the search window`() {
        // Beyond the window the honest answer is no alignment, not a coincidence found by sliding
        // far enough. Whatever is returned, it must be an offset the search was allowed to reach.
        val original = RecordingFingerprinter.fingerprint(music(7))
        val far = RecordingFingerprinter.fingerprint(shifted(music(7), frames = 450))

        val alignment = RecordingFingerprinter.aligned(original, far)
        // +-3 of refine either side of the widest bin the vote may occupy.
        assertTrue(
            "offset ${alignment.offsetFrames} is outside the search window",
            alignment.offsetFrames in -403..403
        )
    }
}
