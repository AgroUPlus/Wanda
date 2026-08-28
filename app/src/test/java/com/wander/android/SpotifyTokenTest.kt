package com.wander.android

import com.wander.android.data.importer.SpotifyPlaylistParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The web token endpoint is the step that breaks first when Spotify changes it, so a failure there
 * has to reach the user instead of looking like "no playlists".
 */
class SpotifyTokenTest {

    private fun parserReturning(status: HttpStatusCode, body: String): SpotifyPlaylistParser {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return SpotifyPlaylistParser(HttpClient(engine))
    }

    @Test
    fun httpErrorFromTokenEndpointIsReported() = runTest {
        val parser = parserReturning(HttpStatusCode.NotFound, "<html>nope</html>")
        val result = parser.fetchUserPlaylists("sp_dc=abc")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("404"))
    }

    @Test
    fun nonJsonTokenResponseIsReported() = runTest {
        val parser = parserReturning(HttpStatusCode.OK, "<html>still not json</html>")
        val result = parser.fetchUserPlaylists("sp_dc=abc")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("unexpected token response"))
    }

    @Test
    fun anonymousSessionAsksTheUserToSignIn() = runTest {
        val parser = parserReturning(
            HttpStatusCode.OK,
            """{"accessToken":"tok","isAnonymous":true}"""
        )
        val result = parser.fetchUserPlaylists(null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().startsWith("Please sign in"))
    }
}
