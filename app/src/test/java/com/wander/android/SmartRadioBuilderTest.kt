package com.wander.android

import com.wander.android.core.audio.features.AcousticFeatures
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.SmartRadioBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the acoustic vectors are and are not allowed to decide.
 *
 * The failure these tests exist to prevent is the tempting one: a radio that ranks by vector and
 * drops what it cannot rank. Only local files are ever measured, so that radio would play the
 * library back at you and would look like it was working.
 */
class SmartRadioBuilderTest {

    private fun track(id: String) = UnifiedTrack(
        id = id,
        source = SourceType.YTMUSIC,
        title = id,
        artist = "Artist",
        album = null,
        durationMs = 200_000
    )

    private fun features(
        tempo: Float,
        energy: Float = 0.5f,
        brightness: Float = 0.5f,
        danceability: Float = 0.5f
    ) = AcousticFeatures(tempo, energy, brightness, danceability, keyX = 0f, keyY = 0f)

    private fun candidate(id: String, features: AcousticFeatures?) =
        SmartRadioBuilder.Candidate(track(id), features)

    @Test
    fun `the nearest track to the seed comes first`() {
        val seed = features(tempo = 0.5f)
        val queue = SmartRadioBuilder.build(
            seed = seed,
            candidates = listOf(
                candidate("far", features(tempo = 0.9f)),
                candidate("near", features(tempo = 0.52f))
            ),
            count = 2
        )

        assertEquals("near", queue.first().id)
    }

    /** The queue walks: each step is judged from the last track, so the set is free to drift. */
    @Test
    fun `the queue walks away from the seed instead of orbiting it`() {
        val queue = SmartRadioBuilder.build(
            seed = features(tempo = 0.10f),
            candidates = listOf(
                candidate("c", features(tempo = 0.30f)),
                candidate("a", features(tempo = 0.15f)),
                candidate("b", features(tempo = 0.22f))
            ),
            count = 3
        )

        assertEquals(listOf("a", "b", "c"), queue.map { it.id })
    }

    /** The point of the whole design: a track nobody measured still gets played. */
    @Test
    fun `unmeasured tracks are not dropped from the queue`() {
        val queue = SmartRadioBuilder.build(
            seed = features(tempo = 0.5f),
            candidates = List(10) { candidate("known$it", features(tempo = 0.5f)) } +
                List(10) { candidate("new$it", null) },
            count = 10
        )

        assertTrue("expected unmeasured tracks in the queue", queue.any { it.id.startsWith("new") })
    }

    @Test
    fun `the exploration share is held even when every measured track fits`() {
        val queue = SmartRadioBuilder.build(
            seed = features(tempo = 0.5f),
            candidates = List(50) { candidate("known$it", features(tempo = 0.5f)) } +
                List(50) { candidate("new$it", null) },
            count = 20
        )

        val explored = queue.count { it.id.startsWith("new") }
        assertEquals((20 * SmartRadioBuilder.EXPLORATION_SHARE).toInt(), explored)
    }

    /** Unmeasured tracks are spread through the queue, not dumped at the end of it. */
    @Test
    fun `unmeasured tracks are interleaved rather than appended`() {
        val queue = SmartRadioBuilder.build(
            seed = features(tempo = 0.5f),
            candidates = List(20) { candidate("known$it", features(tempo = 0.5f)) } +
                List(5) { candidate("new$it", null) },
            count = 20
        )

        val positions = queue.withIndex().filter { it.value.id.startsWith("new") }.map { it.index }
        assertTrue("expected an unmeasured track in the first half, got $positions", positions.any { it < 10 })
    }

    /** A jump nothing bridges ends the walk; it is not smoothed over with a jarring segue. */
    @Test
    fun `a track far from everything is not forced into the queue`() {
        val queue = SmartRadioBuilder.build(
            seed = features(tempo = 0.0f, energy = 0f, brightness = 0f, danceability = 0f),
            candidates = listOf(
                candidate("outlier", features(tempo = 1f, energy = 1f, brightness = 1f, danceability = 1f))
            ),
            count = 5
        )

        assertTrue("expected the outlier to be passed over, got $queue", queue.isEmpty())
    }

    /** With no vector for the seed there is nothing to rank against, and we say so by not ranking. */
    @Test
    fun `an unmeasured seed returns the pool untouched`() {
        val candidates = listOf(
            candidate("b", features(tempo = 0.9f)),
            candidate("a", features(tempo = 0.1f))
        )

        val queue = SmartRadioBuilder.build(seed = null, candidates = candidates, count = 5)

        assertEquals(listOf("b", "a"), queue.map { it.id })
    }

    @Test
    fun `a track appearing in both the source radio and the library is played once`() {
        val queue = SmartRadioBuilder.build(
            seed = features(tempo = 0.5f),
            candidates = listOf(
                candidate("same", features(tempo = 0.5f)),
                candidate("same", features(tempo = 0.5f))
            ),
            count = 5
        )

        assertEquals(1, queue.size)
    }
}
