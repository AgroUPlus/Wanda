package com.wander.android

import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.SpotifyPlaylistParser
import com.wander.android.data.importer.TextPlaylistParser
import com.wander.android.data.importer.YouTubePlaylistParser
import io.ktor.client.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportTest {

    @Test
    fun platformDetection() {
        assertEquals(
            PlatformType.SPOTIFY,
            PlatformType.detect("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123")
        )
        assertEquals(
            PlatformType.DEEZER,
            PlatformType.detect("https://www.deezer.com/en/playlist/908622995")
        )
        assertEquals(
            PlatformType.YOUTUBE,
            PlatformType.detect("https://music.youtube.com/playlist?list=PL4fGSIqsQ87508gZ5r0Lq")
        )
        assertEquals(
            PlatformType.APPLE_MUSIC,
            PlatformType.detect("https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb")
        )
        assertEquals(
            PlatformType.PLAIN_TEXT,
            PlatformType.detect("Daft Punk - One More Time\nJustice - Genesis")
        )
    }

    @Test
    fun spotifyPlaylistIdExtraction() {
        val parser = SpotifyPlaylistParser(HttpClient())
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            parser.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123xyz")
        )
        assertEquals(
            "7ABCxyz99",
            parser.extractPlaylistId("spotify:playlist:7ABCxyz99")
        )
    }

    @Test
    fun deezerPlaylistIdExtraction() {
        val parser = DeezerPlaylistParser(HttpClient())
        assertEquals(
            "908622995",
            parser.extractPlaylistId("https://www.deezer.com/fr/playlist/908622995")
        )
        assertEquals(
            "123456",
            parser.extractPlaylistId("https://deezer.com/playlist/123456")
        )
    }

    @Test
    fun youTubePlaylistIdExtraction() {
        val regex = Regex("""[?&]list=([a-zA-Z0-9_-]+)""")
        val url = "https://music.youtube.com/playlist?list=PL4fGSIqsQ87508gZ5r0Lq&si=abc"
        assertEquals(
            "PL4fGSIqsQ87508gZ5r0Lq",
            regex.find(url)?.groupValues?.getOrNull(1)
        )
    }

    @Test
    fun textPlaylistParsing() {
        val parser = TextPlaylistParser()
        val text = """
            # My Favorite Tracks
            Daft Punk - One More Time
            Justice – D.A.N.C.E.
            Around The World by Daft Punk
            Harder, Better, Faster, Stronger
        """.trimIndent()

        val result = parser.parse(text)
        assertTrue(result.isSuccess)
        val playlist = result.getOrThrow()
        assertEquals(4, playlist.tracks.size)

        assertEquals("Daft Punk", playlist.tracks[0].artist)
        assertEquals("One More Time", playlist.tracks[0].title)

        assertEquals("Justice", playlist.tracks[1].artist)
        assertEquals("D.A.N.C.E.", playlist.tracks[1].title)

        assertEquals("Daft Punk", playlist.tracks[2].artist)
        assertEquals("Around The World", playlist.tracks[2].title)

        assertEquals("Harder, Better, Faster, Stronger", playlist.tracks[3].title)
    }
}
