package com.wander.android

import com.wander.android.data.importer.TextPlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPlaylistParserTest {
    private val parser = TextPlaylistParser()
    private fun parse(text: String) = parser.parse(text).getOrThrow()

    @Test
    fun readsTheLabelAndDurationFromExtinfAndIgnoresTheAddressBelowIt() {
        val playlist = parse(
            """
            #EXTM3U
            #PLAYLIST:Road trip
            #EXTINF:215,Daft Punk - One More Time
            content://media/external/audio/media/470
            #EXTINF:-1,Justice – D.A.N.C.E.
            /storage/emulated/0/Music/Justice/03 - DANCE.m4a
            """.trimIndent()
        )

        assertEquals("Road trip", playlist.title)
        assertEquals(listOf("One More Time", "D.A.N.C.E."), playlist.tracks.map { it.title })
        assertEquals(listOf("Daft Punk", "Justice"), playlist.tracks.map { it.artist })
        assertEquals(listOf(215_000L, 0L), playlist.tracks.map { it.durationMs })
    }

    @Test
    fun aPlainFileOfPathsFallsBackToTheFileName() {
        val playlist = parse(
            """
            /storage/emulated/0/Music/Radiohead - Nude.m4a
            C:\Music\Portishead\01. Roads.flac
            https://example.com/audio/Massive%20Attack%20-%20Teardrop.mp3?token=abc
            content://media/external/audio/media/470
            """.trimIndent()
        )

        assertEquals(listOf("Nude", "Roads", "Teardrop"), playlist.tracks.map { it.title })
        assertEquals("Radiohead", playlist.tracks[0].artist)
        assertEquals("Massive Attack", playlist.tracks[2].artist)
    }

    @Test
    fun exportedLocalTracksComeBackWithTheirNames() {
        // What M3uWriter produces for a device file: a label, then its content URI.
        val playlist = parse("#EXTM3U\n#EXTINF:200,Air - La Femme d'Argent\ncontent://media/external/audio/media/12\n")

        assertEquals("La Femme d'Argent", playlist.tracks.single().title)
        assertEquals("Air", playlist.tracks.single().artist)
    }

    @Test
    fun oneLabelPerLineStillWorks() {
        val playlist = parse("# comment\nDaft Punk - One More Time\nAround The World by Daft Punk\nHarder, Better, Faster, Stronger")

        assertEquals(3, playlist.tracks.size)
        assertEquals("Daft Punk", playlist.tracks[1].artist)
        assertEquals("Unknown Artist", playlist.tracks[2].artist)
        assertTrue(playlist.title.contains("3"))
    }

    @Test
    fun failsClearlyWhenThereIsNothingToRead() {
        assertTrue(parser.parse("#EXTM3U\n\n# nothing\n").isFailure)
    }
}
