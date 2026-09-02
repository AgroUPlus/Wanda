package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.repository.AlbumResolution
import com.wander.android.data.repository.UniversalAlbumLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A link that describes a record instead of pointing at a server.
 *
 * The thing being pinned is that it survives the trip: a link is typed into a chat app, wrapped,
 * unwrapped and pasted, and a title with an ampersand or an accent in it must arrive intact.
 */
class UniversalAlbumLinkTest {

    @Test
    fun `a link survives the round trip`() {
        val link = UniversalAlbumLink("Kid A", "Radiohead", year = 2000, trackCount = 10)
        assertEquals(link, UniversalAlbumLink.parse(link.toUri()))
    }

    /** Titles have ampersands, slashes, question marks and accents in them. */
    @Test
    fun `awkward titles survive the round trip`() {
        val link = UniversalAlbumLink("Songs of Love & Hate", "Leonard Cohen")
        assertEquals(link, UniversalAlbumLink.parse(link.toUri()))

        val accented = UniversalAlbumLink("Où est le bonheur ?", "Étienne Daho")
        assertEquals(accented, UniversalAlbumLink.parse(accented.toUri()))
    }

    /** The hints are optional; a link without them still resolves. */
    @Test
    fun `a link without hints is still a link`() {
        val link = UniversalAlbumLink("Kid A", "Radiohead")
        val parsed = UniversalAlbumLink.parse(link.toUri())
        assertEquals("Kid A", parsed?.title)
        assertNull(parsed?.year)
        assertNull(parsed?.trackCount)
    }

    /** "Greatest Hits" with no artist resolves to somebody's greatest hits. Refuse it. */
    @Test
    fun `a link with no artist is refused`() {
        assertNull(UniversalAlbumLink.parse("wanda://album?title=Greatest%20Hits"))
        assertNull(UniversalAlbumLink.parse("wanda://album?artist=Radiohead"))
    }

    @Test
    fun `other links are not album links`() {
        assertFalse(UniversalAlbumLink.matches("wanda://jam?code=ABC"))
        assertFalse(UniversalAlbumLink.matches("https://frwd.top/listen?id=7"))
        assertTrue(UniversalAlbumLink.matches("wanda://album?title=A&artist=B"))
    }
}

/**
 * Which of the albums a search returned is the one the link meant.
 *
 * Strictness is the whole design here: a link to *Kid A* that opens *Kid A Mnesia* has technically
 * resolved, and is a worse outcome than telling the recipient it was not found.
 */
class AlbumResolutionTest {

    private fun album(
        title: String,
        artist: String = "Radiohead",
        source: SourceType = SourceType.NAVIDROME,
        year: Int? = null,
        songCount: Int = 0
    ) = UnifiedAlbum(
        id = "$source:$title",
        source = source,
        title = title,
        artist = artist,
        year = year,
        songCount = songCount
    )

    private val link = UniversalAlbumLink("Kid A", "Radiohead", year = 2000, trackCount = 10)

    @Test
    fun `an exact match resolves`() {
        assertEquals("Kid A", AlbumResolution.bestMatch(link, listOf(album("Kid A")))?.title)
    }

    /** Punctuation and case differ between backends and neither spelling is wrong. */
    @Test
    fun `spelling differences do not prevent a match`() {
        assertEquals(
            "kid a",
            AlbumResolution.bestMatch(link, listOf(album("kid a")))?.title
        )
    }

    /** The failure this is written to prevent. */
    @Test
    fun `a longer album with the same prefix is not a match`() {
        assertNull(AlbumResolution.bestMatch(link, listOf(album("Kid A Mnesia"))))
    }

    @Test
    fun `the right album by the wrong artist is not a match`() {
        assertNull(AlbumResolution.bestMatch(link, listOf(album("Kid A", artist = "Someone Else"))))
    }

    @Test
    fun `nothing matching resolves to nothing`() {
        assertNull(AlbumResolution.bestMatch(link, emptyList()))
        assertNull(AlbumResolution.bestMatch(link, listOf(album("OK Computer"))))
    }

    /** Someone with a server *and* a subscription has the record twice; theirs should win. */
    @Test
    fun `the caller's preference order breaks a tie`() {
        val candidates = listOf(
            album("Kid A", source = SourceType.NAVIDROME),
            album("Kid A", source = SourceType.YTMUSIC)
        )
        assertEquals(SourceType.NAVIDROME, AlbumResolution.bestMatch(link, candidates)?.source)
    }

    /** …unless a hint says otherwise: the year tells a reissue from the original. */
    @Test
    fun `the year hint outranks the preference order`() {
        val candidates = listOf(
            album("Kid A", source = SourceType.NAVIDROME, year = 2009),
            album("Kid A", source = SourceType.YTMUSIC, year = 2000)
        )
        assertEquals(2000, AlbumResolution.bestMatch(link, candidates)?.year)
    }

    @Test
    fun `the track count decides when the year cannot`() {
        val candidates = listOf(
            album("Kid A", source = SourceType.NAVIDROME, songCount = 22),
            album("Kid A", source = SourceType.YTMUSIC, songCount = 10)
        )
        assertEquals(10, AlbumResolution.bestMatch(link, candidates)?.songCount)
    }
}
