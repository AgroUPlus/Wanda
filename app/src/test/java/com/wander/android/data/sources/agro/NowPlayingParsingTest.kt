package com.wander.android.data.sources.agro

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A private session publishes blank placeholders where the track would be, the real one sealed
 * beside them. Reading those as missing crashed every friend of that account on launch.
 */
class NowPlayingParsingTest {

    private fun presence(trackUri: String, artist: String): JsonObject = buildJsonObject {
        put("username", "alpha")
        put("trackUri", trackUri)
        put("trackTitle", "Private Session")
        put("artistName", artist)
        put("updatedAt", "2026-10-04T14:00:00+00:00")
    }

    @Test
    fun a_private_sessions_blank_placeholders_parse() {
        val now = presence(trackUri = "", artist = "").toNowPlaying()
        assertEquals("", now.artistName)
        assertEquals("", now.trackUri)
    }

    @Test(expected = IllegalStateException::class)
    fun an_absent_artist_is_still_the_server_breaking_its_schema() {
        val json = presence(trackUri = "encrypted", artist = "")
        JsonObject(json - "artistName").toNowPlaying()
    }
}
