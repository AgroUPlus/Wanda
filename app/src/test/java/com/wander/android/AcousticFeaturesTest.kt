package com.wander.android

import com.wander.android.core.audio.features.AcousticFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vector's own arithmetic: what "close" means, and what the key circle buys. */
class AcousticFeaturesTest {

    private fun features(
        tempo: Float = 0.5f,
        energy: Float = 0.5f,
        brightness: Float = 0.5f,
        danceability: Float = 0.5f,
        keyX: Float = 0f,
        keyY: Float = 0f
    ) = AcousticFeatures(tempo, energy, brightness, danceability, keyX, keyY)

    @Test
    fun `a track is at no distance from itself`() {
        val track = features(tempo = 0.3f, energy = 0.8f)
        assertEquals(0f, track.distanceTo(track), 1e-6f)
    }

    /** Tempo is weighted highest, so an equal difference in it must cost more than in brightness. */
    @Test
    fun `tempo separates two tracks more than brightness does`() {
        val seed = features()
        val tempoApart = seed.distanceTo(features(tempo = 0.9f))
        val brightApart = seed.distanceTo(features(brightness = 0.9f))

        assertTrue("$tempoApart should exceed $brightApart", tempoApart > brightApart)
    }

    /**
     * The reason the key is a point and not a number: C and B are a semitone apart, and a
     * pitch-class integer would have made them the two furthest-apart keys in the vector.
     */
    @Test
    fun `neighbouring keys sit near each other on the circle`() {
        val (cx, cy) = AcousticFeatures.keyPoint(pitchClass = 0, strength = 1f)
        val (gx, gy) = AcousticFeatures.keyPoint(pitchClass = 7, strength = 1f)
        val (fSharpX, fSharpY) = AcousticFeatures.keyPoint(pitchClass = 6, strength = 1f)

        val cToG = features(keyX = cx, keyY = cy).distanceTo(features(keyX = gx, keyY = gy))
        val cToFSharp = features(keyX = cx, keyY = cy)
            .distanceTo(features(keyX = fSharpX, keyY = fSharpY))

        // C to its own dominant is the closest musical move there is; C to the tritone is the
        // furthest. The circle has to reproduce that ordering or the axis is noise.
        assertTrue("fifth $cToG should be nearer than tritone $cToFSharp", cToG < cToFSharp)
    }

    /** A track with no tonal centre must not be asserted to be in C. */
    @Test
    fun `an atonal track sits at the origin of the key circle`() {
        val (x, y) = AcousticFeatures.keyPoint(pitchClass = 0, strength = 0f)
        assertEquals(0f, x, 1e-6f)
        assertEquals(0f, y, 1e-6f)
    }

    @Test
    fun `tempo normalisation round-trips within the measured range`() {
        assertEquals(120f, AcousticFeatures.bpmOf(AcousticFeatures.normaliseTempo(120f)), 0.01f)
    }

    /** Out-of-range tempos clamp rather than running off the axis and distorting every distance. */
    @Test
    fun `tempos outside the range clamp to its ends`() {
        assertEquals(0f, AcousticFeatures.normaliseTempo(30f), 1e-6f)
        assertEquals(1f, AcousticFeatures.normaliseTempo(240f), 1e-6f)
    }
}
