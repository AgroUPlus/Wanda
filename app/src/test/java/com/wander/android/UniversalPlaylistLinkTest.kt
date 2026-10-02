package com.wander.android

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.UniversalPlaylistLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class UniversalPlaylistLinkTest {

    private fun track(i: Int, title: String = "Track $i", artist: String = "Artist $i") = UnifiedTrack(
        id = "ytm:$i", source = SourceType.YTMUSIC, title = title, artist = artist,
        album = "Album $i", durationMs = 200_000L + i
    )

    private fun linkWith(d: String, name: String = "Mix") = "wanda://playlist?name=$name&d=$d"

    @Test
    fun aPlaylistSurvivesTheRoundTrip() {
        val tracks = listOf(track(1, "Sigur Rós — Hoppípolla", "Sigur Rós"), track(2, "Été & ça", "Mélissa"))
        val uri = UniversalPlaylistLink.from("Road trip & café", tracks)!!.toUri()

        val parsed = UniversalPlaylistLink.parse(uri)!!

        assertEquals("Road trip & café", parsed.name)
        assertEquals(listOf("Sigur Rós — Hoppípolla", "Été & ça"), parsed.tracks.map { it.title })
        assertEquals(listOf("Sigur Rós", "Mélissa"), parsed.tracks.map { it.artist })
        assertEquals("Album 2", parsed.tracks[1].album)
        assertEquals(200_002L, parsed.tracks[1].durationMs)
    }

    @Test
    fun aFullLengthPlaylistStaysAShareableSize() {
        val uri = UniversalPlaylistLink.from("Big", (1..UniversalPlaylistLink.MAX_TRACKS).map { track(it) })!!.toUri()

        assertEquals(UniversalPlaylistLink.MAX_TRACKS, UniversalPlaylistLink.parse(uri)!!.tracks.size)
        assertTrue("link was ${uri.length} chars", uri.length < 16_000)
    }

    @Test
    fun refusesToMakeALinkTooLongOrEmpty() {
        assertNull(UniversalPlaylistLink.from("Big", (1..UniversalPlaylistLink.MAX_TRACKS + 1).map { track(it) }))
        assertNull(UniversalPlaylistLink.from("Empty", emptyList()))
        assertNull(UniversalPlaylistLink.from("  ", listOf(track(1))))
    }

    @Test
    fun matchesOnlyItsOwnHost() {
        assertTrue(UniversalPlaylistLink.matches("wanda://playlist?name=a&d=b"))
        assertFalse(UniversalPlaylistLink.matches("wanda://album?title=a&artist=b"))
        assertFalse(UniversalPlaylistLink.matches("https://example.com/playlist?name=a"))
    }

    @Test
    fun rejectsDamagedLinks() {
        assertNull(UniversalPlaylistLink.parse("wanda://playlist?name=Mix"))
        assertNull(UniversalPlaylistLink.parse(linkWith("not-base64!!")))
        assertNull(UniversalPlaylistLink.parse(linkWith(Base64.getUrlEncoder().withoutPadding().encodeToString("plain".toByteArray()))))
        assertNull(UniversalPlaylistLink.parse(linkWith(gzipped("""{"not":"a list"}"""))))
        assertNull(UniversalPlaylistLink.parse(linkWith(gzipped("[]"))))
        assertNull(UniversalPlaylistLink.parse("wanda://playlist?name=&d=" + gzipped("""[{"title":"A"}]""")))
    }

    @Test
    fun refusesALinkThatUnpacksToFarMoreThanAnyoneSent() {
        // A few hundred bytes of gzip that expand to a megabyte: the decompression bomb shape.
        val bomb = gzipped("[" + "\"a\",".repeat(400_000) + "\"a\"]")

        assertNull(UniversalPlaylistLink.parse(linkWith(bomb)))
    }

    @Test
    fun capsWhatAForeignLinkClaimsToHold() {
        val many = (1..500).joinToString(",", "[", "]") { """{"title":"T$it","artist":"A"}""" }

        assertEquals(UniversalPlaylistLink.MAX_TRACKS, UniversalPlaylistLink.parse(linkWith(gzipped(many)))!!.tracks.size)
    }

    @Test
    fun skipsEntriesWithNoTitle() {
        val parsed = UniversalPlaylistLink.parse(
            linkWith(gzipped("""[{"title":" "},{"title":"Keep","artist":"A"}]"""))
        )
        assertNotNull(parsed)
        assertEquals(listOf("Keep"), parsed!!.tracks.map { it.title })
    }

    private fun gzipped(text: String): String {
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
