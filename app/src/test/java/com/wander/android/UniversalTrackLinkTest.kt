package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.TrackResolution
import com.wander.android.data.repository.UniversalTrackLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A link that describes a recording instead of pointing at a server.
 *
 * Same concern as `UniversalAlbumLinkTest`: a title with an ampersand or an accent must survive
 * being typed into a chat app, wrapped, unwrapped and pasted.
 */
class UniversalTrackLinkTest {

    @Test
    fun `a link survives the round trip`() {
        val link = UniversalTrackLink("Idioteque", "Radiohead", album = "Kid A", durationMs = 341_000)
        assertEquals(link, UniversalTrackLink.parse(link.toUri()))
    }

    @Test
    fun `awkward titles survive the round trip`() {
        val link = UniversalTrackLink("Chelsea Hotel #2", "Leonard Cohen")
        assertEquals(link, UniversalTrackLink.parse(link.toUri()))

        val accented = UniversalTrackLink("Résiste", "France Gall")
        assertEquals(accented, UniversalTrackLink.parse(accented.toUri()))
    }

    @Test
    fun `a link without hints is still a link`() {
        val link = UniversalTrackLink("Idioteque", "Radiohead")
        val parsed = UniversalTrackLink.parse(link.toUri())
        assertEquals("Idioteque", parsed?.title)
        assertNull(parsed?.album)
        assertNull(parsed?.durationMs)
    }

    @Test
    fun `a link with no artist is refused`() {
        assertNull(UniversalTrackLink.parse("wanda://track?title=Idioteque"))
        assertNull(UniversalTrackLink.parse("wanda://track?artist=Radiohead"))
    }

    @Test
    fun `other links are not track links`() {
        assertFalse(UniversalTrackLink.matches("wanda://album?title=A&artist=B"))
        assertFalse(UniversalTrackLink.matches("https://frwd.top/listen?id=7"))
        assertTrue(UniversalTrackLink.matches("wanda://track?title=A&artist=B"))
    }
}

/**
 * Which of the tracks a search returned is the one the link meant.
 */
class TrackResolutionTest {

    private fun track(
        title: String,
        artist: String = "Radiohead",
        source: SourceType = SourceType.NAVIDROME,
        album: String? = null,
        durationMs: Long = 0
    ) = UnifiedTrack(
        id = "$source:$title",
        source = source,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs
    )

    private val link = UniversalTrackLink("Idioteque", "Radiohead", album = "Kid A", durationMs = 341_000)

    @Test
    fun `an exact match resolves`() {
        assertEquals("Idioteque", TrackResolution.bestMatch(link, listOf(track("Idioteque")))?.title)
    }

    @Test
    fun `spelling differences do not prevent a match`() {
        assertEquals(
            "idioteque",
            TrackResolution.bestMatch(link, listOf(track("idioteque")))?.title
        )
    }

    @Test
    fun `a longer title with the same prefix is not a match`() {
        assertNull(TrackResolution.bestMatch(link, listOf(track("Idioteque (Live)"))))
    }

    @Test
    fun `the right title by the wrong artist is not a match`() {
        assertNull(TrackResolution.bestMatch(link, listOf(track("Idioteque", artist = "Someone Else"))))
    }

    @Test
    fun `nothing matching resolves to nothing`() {
        assertNull(TrackResolution.bestMatch(link, emptyList()))
        assertNull(TrackResolution.bestMatch(link, listOf(track("Karma Police"))))
    }

    @Test
    fun `the caller's preference order breaks a tie`() {
        val candidates = listOf(
            track("Idioteque", source = SourceType.NAVIDROME),
            track("Idioteque", source = SourceType.YTMUSIC)
        )
        assertEquals(SourceType.NAVIDROME, TrackResolution.bestMatch(link, candidates)?.source)
    }

    @Test
    fun `the album hint outranks the preference order`() {
        val candidates = listOf(
            track("Idioteque", source = SourceType.NAVIDROME, album = "Live Recordings"),
            track("Idioteque", source = SourceType.YTMUSIC, album = "Kid A")
        )
        assertEquals("Kid A", TrackResolution.bestMatch(link, candidates)?.album)
    }

    @Test
    fun `the duration hint decides when the album cannot`() {
        val candidates = listOf(
            track("Idioteque", source = SourceType.NAVIDROME, durationMs = 500_000),
            track("Idioteque", source = SourceType.YTMUSIC, durationMs = 341_500)
        )
        assertEquals(341_500L, TrackResolution.bestMatch(link, candidates)?.durationMs)
    }
}
