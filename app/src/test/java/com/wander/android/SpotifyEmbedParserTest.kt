package com.wander.android

import com.wander.android.data.importer.SpotifyPlaylistParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The embed page's markup is what Spotify is likeliest to change, so a miss must reach the user. */
class SpotifyEmbedParserTest {

    private fun parserReturning(status: HttpStatusCode, body: String) =
        SpotifyPlaylistParser(HttpClient(MockEngine { respond(content = body, status = status) }))

    private fun embedPage(entityJson: String) =
        """<html><script id="__NEXT_DATA__" type="application/json">""" +
            """{"props":{"pageProps":{"state":{"data":{"entity":$entityJson}}}}}</script></html>"""

    private val link = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun readsTitleCoverAndTracks() = runTest {
        val page = embedPage(
            """{"title":"Road trip","coverArt":{"sources":[{"url":"https://i.scdn.co/cover"}]},
               "trackList":[{"title":"Song A","subtitle":"Artist 1,${' '}Artist 2","duration":181270},
                            {"title":"","subtitle":"skipped","duration":1}]}"""
        )
        val playlist = parserReturning(HttpStatusCode.OK, page).parse(link).getOrThrow()

        assertEquals("Road trip", playlist.title)
        assertEquals("https://i.scdn.co/cover", playlist.coverUrl)
        assertEquals(1, playlist.tracks.size)
        assertEquals("Artist 1, Artist 2", playlist.tracks[0].artist)
        assertEquals(181270L, playlist.tracks[0].durationMs)
        assertNull(playlist.tracks[0].album)
    }

    @Test
    fun httpErrorMentionsPrivatePlaylists() = runTest {
        val result = parserReturning(HttpStatusCode.NotFound, "nope").parse(link)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("404") && message.contains("anyone with the link"))
    }

    @Test
    fun pageWithoutPlaylistDataIsReported() = runTest {
        val result = parserReturning(HttpStatusCode.OK, "<html>changed markup</html>").parse(link)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("no playlist data"))
    }

    @Test
    fun emptyTrackListIsReported() = runTest {
        val result = parserReturning(HttpStatusCode.OK, embedPage("""{"title":"x","trackList":[]}""")).parse(link)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("No tracks"))
    }
}
