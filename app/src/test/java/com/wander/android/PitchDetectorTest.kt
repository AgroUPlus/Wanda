package com.wander.android

import com.wander.android.core.audio.fingerprint.AudioFormat
import com.wander.android.core.audio.melody.MelodyContour
import com.wander.android.core.audio.melody.PitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * YIN against tones this test builds itself, so a failure means the detector changed rather than
 * that some recording is unusual.
 *
 * The octave error is what these are really guarding. A pitch tracker that answers 220 for a 440
 * tone is not a little wrong — every interval after it is wrong too, and a contour with one octave
 * slip in it matches nothing.
 */
class PitchDetectorTest {

    private val detector = PitchDetector()

    /** A pure tone, and a few harmonics, since a hummed note is never a sine. */
    private fun tone(hz: Float, seconds: Float, harmonics: Int = 3): FloatArray {
        val count = (AudioFormat.SAMPLE_RATE * seconds).toInt()
        return FloatArray(count) { i ->
            val t = i.toDouble() / AudioFormat.SAMPLE_RATE
            var value = 0.0
            for (h in 1..harmonics) value += sin(2 * PI * hz * h * t) / h
            (value * 0.4).toFloat()
        }
    }

    private fun medianPitch(samples: FloatArray): Float =
        detector.track(samples).filter { it > 0f }.sorted().let { voiced ->
            if (voiced.isEmpty()) 0f else voiced[voiced.size / 2]
        }

    @Test
    fun `a 440 Hz tone reads as 440`() {
        assertEquals(440f, medianPitch(tone(440f, 1f)), 8f)
    }

    /** The failure the cumulative-mean normalisation exists to prevent. */
    @Test
    fun `a rich tone is not heard an octave down`() {
        val measured = medianPitch(tone(220f, 1f, harmonics = 6))
        assertEquals("expected 220, an octave error would give 110", 220f, measured, 6f)
    }

    @Test
    fun `a low male voice and a whistle are both in range`() {
        assertEquals(98f, medianPitch(tone(98f, 1f)), 4f)
        assertEquals(880f, medianPitch(tone(880f, 1f, harmonics = 1)), 20f)
    }

    /** Silence must read as unvoiced, not as some arbitrary period found in nothing. */
    @Test
    fun `silence yields no pitch`() {
        val pitches = detector.track(FloatArray(AudioFormat.SAMPLE_RATE))
        assertTrue("silence produced pitches: ${pitches.filter { it > 0f }.take(5)}",
            pitches.none { it > 0f })
    }

    /** End to end: three tones become three notes with the right intervals between them. */
    @Test
    fun `a three-note phrase becomes a contour with the right intervals`() {
        // C4, E4, G4 — a major triad, so four semitones then three.
        val phrase = tone(261.63f, 0.5f) + tone(329.63f, 0.5f) + tone(392.00f, 0.5f)
        val contour = MelodyContour.fromPitchTrack(
            detector.track(phrase),
            AudioFormat.FRAMES_PER_SECOND
        )

        assertEquals(3, contour.size)
        assertEquals(0, contour.notes[0].delta)
        assertEquals(4, contour.notes[1].delta)
        assertEquals(3, contour.notes[2].delta)
    }
}
