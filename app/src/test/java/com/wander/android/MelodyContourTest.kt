package com.wander.android

import com.wander.android.core.audio.melody.ContourMatcher
import com.wander.android.core.audio.melody.MelodyContour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What humming is allowed to find.
 *
 * The two invariances are the feature: nobody hums in the right key and nobody hums in time, so a
 * matcher that requires either is a matcher that never matches. The thresholds are the other half
 * — a warping distance can be made small between any two melodies if the bar is low enough, and a
 * confident wrong answer is worse than no answer.
 */
class MelodyContourTest {

    private fun contour(vararg pairs: Pair<Int, Int>) =
        MelodyContour(pairs.map { (delta, ticks) -> MelodyContour.Note(delta, ticks) })

    /** The tune of "Twinkle Twinkle": up a fifth, up a tone, down, down, down, down. */
    private val twinkle = contour(0 to 5, 0 to 5, 7 to 5, 0 to 5, 2 to 5, 0 to 5, -2 to 10)

    @Test
    fun `a contour survives the round trip through bytes`() {
        val restored = MelodyContour.fromBytes(twinkle.toBytes())
        assertEquals(twinkle.notes, restored.notes)
    }

    /** Two bytes per note, as specified — the storage budget the whole design rests on. */
    @Test
    fun `a contour costs two bytes per note`() {
        assertEquals(twinkle.size * 2, twinkle.toBytes().size)
    }

    /** A note held longer than 1.27 s must not come back negative. */
    @Test
    fun `a long note round-trips as an unsigned duration`() {
        val long = contour(0 to 200)
        assertEquals(200, MelodyContour.fromBytes(long.toBytes()).notes[0].ticks)
    }

    /** Key invariance: the intervals are the melody, so humming it lower changes nothing at all. */
    @Test
    fun `the same tune hummed in another key is the same contour`() {
        // Deltas are relative, so transposition is literally not representable — which is the
        // point. This pins that the representation cannot regress to absolute pitches.
        val transposed = contour(0 to 5, 0 to 5, 7 to 5, 0 to 5, 2 to 5, 0 to 5, -2 to 10)
        assertEquals(0f, ContourMatcher.distance(twinkle, transposed), 1e-6f)
    }

    /** Tempo invariance: hummed at half speed, it is still the same tune. */
    @Test
    fun `the same tune hummed slower still matches`() {
        val slow = contour(0 to 10, 0 to 10, 7 to 10, 0 to 10, 2 to 10, 0 to 10, -2 to 20)
        assertTrue(
            "a halved tempo must stay within the threshold",
            ContourMatcher.distance(slow, twinkle) <= ContourMatcher.MAX_DISTANCE
        )
    }

    /** People hum the chorus, and the stored contour is the whole song. */
    @Test
    fun `a hum matches a stretch in the middle of a longer melody`() {
        val song = contour(5 to 4, -3 to 4, 1 to 4) +
            twinkle +
            contour(4 to 4, -1 to 4)
        assertTrue(
            "a subsequence must match",
            ContourMatcher.distance(twinkle, song) <= ContourMatcher.MAX_DISTANCE
        )
    }

    @Test
    fun `a different tune does not match`() {
        val other = contour(0 to 5, -5 to 5, -2 to 5, 9 to 5, 1 to 5, -7 to 5, 3 to 10)
        assertTrue(
            "unrelated melodies must clear the threshold",
            ContourMatcher.distance(other, twinkle) > ContourMatcher.MAX_DISTANCE
        )
    }

    /** A hum a couple of notes long is not a melody, and matching it would be luck. */
    @Test
    fun `too short a hum is refused rather than matched`() {
        val fragment = contour(0 to 5, 2 to 5)
        assertEquals(Float.MAX_VALUE, ContourMatcher.distance(fragment, twinkle), 0f)
    }

    /** A wrong interval must cost more than a note held too long. */
    @Test
    fun `melody outweighs rhythm`() {
        val wrongRhythm = contour(0 to 20, 0 to 2, 7 to 15, 0 to 3, 2 to 12, 0 to 4, -2 to 30)
        val wrongNotes = contour(0 to 5, 0 to 5, 4 to 5, 0 to 5, 5 to 5, 0 to 5, -6 to 10)

        assertTrue(
            "rhythm errors must cost less than interval errors",
            ContourMatcher.distance(wrongRhythm, twinkle) <
                ContourMatcher.distance(wrongNotes, twinkle)
        )
    }

    private operator fun MelodyContour.plus(other: MelodyContour) =
        MelodyContour(notes + other.notes)
}
