package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.RadioPool
import com.wander.android.data.repository.RecordingRules
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A station around a song is never that song again, under any name or upload.
 *
 * The failure behind these tests: a radio that led with every other track titled like the seed,
 * which reads to the listener as the app matching by spelling rather than by sound.
 */
class RadioPoolTest {

    private fun track(
        id: String,
        title: String,
        artist: String = "Artist",
        source: SourceType = SourceType.YTMUSIC
    ) = UnifiedTrack(
        id = id,
        source = source,
        title = title,
        artist = artist,
        album = null,
        durationMs = 200_000
    )

    private val seed = track("ytm:seed", "Signals")

    private fun pool(vararg candidates: UnifiedTrack) =
        RadioPool.of(seed, candidates.toList(), RecordingRules.NONE).map { it.id }

    @Test
    fun `the seed itself is never a candidate`() {
        assertEquals(listOf("ytm:other"), pool(seed, track("ytm:other", "Elsewhere")))
    }

    @Test
    fun `the seed on another backend is never a candidate`() {
        val rendition = track("navidrome:seed", "Signals", source = SourceType.NAVIDROME)
        assertEquals(emptyList<String>(), pool(rendition))
    }

    @Test
    fun `other uploads of the same song are dropped whoever performs them`() {
        assertEquals(
            listOf("ytm:keep"),
            pool(
                track("ytm:live", "Signals (Live)"),
                track("ytm:cover", "Signals", artist = "Somebody Else"),
                track("ytm:audio", "Signals (Official Audio)"),
                track("ytm:keep", "Static")
            )
        )
    }

    @Test
    fun `a title that only contains the seed's is a different song and stays`() {
        assertEquals(listOf("ytm:longer"), pool(track("ytm:longer", "Signals in the Dark")))
    }

    @Test
    fun `duplicates collapse to the first copy`() {
        val first = track("ytm:a", "Static")
        val copy = track("navidrome:a", "Static", source = SourceType.NAVIDROME)
        assertEquals(listOf("ytm:a"), pool(first, copy))
    }
}
