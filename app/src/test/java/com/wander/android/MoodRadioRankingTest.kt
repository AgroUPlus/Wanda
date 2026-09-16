package com.wander.android

import com.wander.android.core.audio.features.AcousticFeatures
import com.wander.android.data.repository.MoodRadioRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which tracks are closest to a point on the Tempo × Energy matrix. */
class MoodRadioRankingTest {

    private fun features(tempo: Float, energy: Float) = AcousticFeatures(
        tempo = tempo,
        energy = energy,
        brightness = 0.5f,
        danceability = 0.5f,
        keyX = 0f,
        keyY = 0f
    )

    @Test
    fun `the closest track ranks first`() {
        val library = mapOf(
            "calm" to features(tempo = 0.1f, energy = 0.1f),
            "loud" to features(tempo = 0.9f, energy = 0.9f)
        )
        val nearest = MoodRadioRanking.nearest(library, tempo = 0.0f, energy = 0.0f, limit = 2)
        assertEquals("calm", nearest.first())
    }

    @Test
    fun `only tempo and energy decide, not brightness or key`() {
        val library = mapOf(
            "brighter" to AcousticFeatures(0.5f, 0.5f, brightness = 1f, danceability = 0f, keyX = 1f, keyY = 0f),
            "duller" to AcousticFeatures(0.5f, 0.5f, brightness = 0f, danceability = 1f, keyX = -1f, keyY = 0f)
        )
        // Identical tempo/energy: both are equally near, and the map's own order decides the tie —
        // the point being that neither is excluded or demoted by the axes the matrix never asked.
        val nearest = MoodRadioRanking.nearest(library, tempo = 0.5f, energy = 0.5f, limit = 2)
        assertEquals(2, nearest.size)
    }

    @Test
    fun `a track with no usable measurement is excluded`() {
        val library = mapOf(
            "silent" to features(tempo = 0f, energy = 0f), // energy == 0f -> not usable
            "real" to features(tempo = 0.5f, energy = 0.5f)
        )
        val nearest = MoodRadioRanking.nearest(library, tempo = 0f, energy = 0f, limit = 5)
        assertEquals(listOf("real"), nearest)
    }

    @Test
    fun `an empty library resolves to nothing`() {
        assertTrue(MoodRadioRanking.nearest(emptyMap(), 0.5f, 0.5f, limit = 10).isEmpty())
    }

    @Test
    fun `limit caps the result even with more candidates available`() {
        val library = (0..9).associate { i -> "t$i" to features(tempo = i / 10f, energy = 0.5f) }
        assertEquals(3, MoodRadioRanking.nearest(library, tempo = 0.5f, energy = 0.5f, limit = 3).size)
    }
}
