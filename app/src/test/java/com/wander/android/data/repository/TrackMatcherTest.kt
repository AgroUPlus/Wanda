package com.wander.android.data.repository

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatcherTest {

    private fun track(
        id: String,
        source: SourceType,
        title: String,
        artist: String,
        durationMs: Long = 0L
    ) = UnifiedTrack(id = id, source = source, title = title, artist = artist, durationMs = durationMs)

    @Test
    fun `prefers the user's own source over a streaming one`() {
        val candidates = listOf(
            track("ytm:1", SourceType.YTMUSIC, "Midnight City", "M83"),
            track("navidrome:1", SourceType.NAVIDROME, "Midnight City", "M83")
        )

        assertEquals("navidrome:1", findBestMatch("Midnight City", "M83", 0L, candidates)?.id)
    }

    @Test
    fun `ignores bracketed noise and featured artists on the imported title`() {
        val candidates = listOf(track("ytm:1", SourceType.YTMUSIC, "Levitating", "Dua Lipa"))

        val match = findBestMatch("Levitating (Official Video) feat. DaBaby", "Dua Lipa", 0L, candidates)

        assertEquals("ytm:1", match?.id)
    }

    @Test
    fun `rejects a candidate whose title does not match`() {
        val candidates = listOf(track("navidrome:1", SourceType.NAVIDROME, "Another Song", "M83"))

        assertNull(findBestMatch("Midnight City", "M83", 0L, candidates))
    }

    @Test
    fun `rejects a weak partial match below the score floor`() {
        val candidates = listOf(track("deezer:1", SourceType.DEEZER, "Midnight City Remix", "Someone Else"))

        assertNull(findBestMatch("Midnight City", "M83", 0L, candidates))
    }

    @Test
    fun `never resolves to an unresolved stub`() {
        val candidates = listOf(track("unresolved:1", SourceType.UNRESOLVED, "Midnight City", "M83"))

        assertNull(findBestMatch("Midnight City", "M83", 0L, candidates))
    }

    @Test
    fun `returns null when there are no candidates`() {
        assertNull(findBestMatch("Midnight City", "M83", 0L, emptyList()))
    }
}
