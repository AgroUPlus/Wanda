package com.wander.android

import com.wander.android.data.importer.M3uWriter
import com.wander.android.data.importer.TextPlaylistParser
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class M3uWriterTest {

    private fun track(title: String, artist: String, durationMs: Long = 0L) = UnifiedTrack(
        id = "navidrome:$title",
        source = SourceType.NAVIDROME,
        title = title,
        artist = artist,
        durationMs = durationMs
    )

    @Test
    fun `an exported playlist imports back with the same tracks and name`() {
        val tracks = listOf(
            track("Staff Roll", "Ryo Nagamatsu", 154_000),
            track("Castle Boss Battle", "Koji Kondo", 0),
            track("Opening", "Ryo Nagamatsu", 61_500)
        )

        val parsed = TextPlaylistParser().parse(M3uWriter.write("Daily Mix 3", tracks)).getOrThrow()

        assertEquals("Daily Mix 3", parsed.title)
        assertEquals(tracks.map { it.title }, parsed.tracks.map { it.title })
        assertEquals(tracks.map { it.artist }, parsed.tracks.map { it.artist })
        assertEquals(listOf(154_000L, 0L, 61_000L), parsed.tracks.map { it.durationMs })
    }

    @Test
    fun `a line break in a title cannot start a line of its own`() {
        val text = M3uWriter.write("Mix", listOf(track("Two\nLines", "Artist")))

        assertEquals(3, text.lines().count { it.isNotBlank() })
        assertEquals("Two Lines", TextPlaylistParser().parse(text).getOrThrow().tracks.single().title)
    }

    @Test
    fun `no address is written for a track`() {
        val text = M3uWriter.write("Mix", listOf(track("Song", "Artist")))

        assertFalse(text.lines().any { it.isNotBlank() && !it.startsWith("#") })
    }

    @Test
    fun `a file name drops characters a file system refuses`() {
        assertEquals("AC_DC_ Live.m3u8", M3uWriter.fileName("AC/DC: Live"))
        assertEquals("playlist.m3u8", M3uWriter.fileName("  "))
    }
}
