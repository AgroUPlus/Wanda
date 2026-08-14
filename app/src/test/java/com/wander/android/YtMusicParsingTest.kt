package com.wander.android

import com.wander.android.data.sources.ytmusic.parseDurationText
import com.wander.android.data.sources.ytmusic.parsePlaylistPanelVideo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Radio results used to arrive titled "Radio Track" with a zero duration because the renderer's
 * metadata was thrown away. These pin the real parsing down.
 */
class YtMusicParsingTest {

    @Test
    fun parsesDurationText() {
        assertEquals(221_000L, parseDurationText("3:41"))
        assertEquals(3_723_000L, parseDurationText("1:02:03"))
        assertEquals(0L, parseDurationText(null))
        assertEquals(0L, parseDurationText("unknown"))
    }

    @Test
    fun parsesRadioEntryMetadata() {
        val renderer = Json.parseToJsonElement(
            """
            {
              "videoId": "abc123",
              "title": { "runs": [ { "text": "Real Title" } ] },
              "longBylineText": { "runs": [
                { "text": "Real Artist" }, { "text": " • " }, { "text": "Real Album" }
              ] },
              "lengthText": { "runs": [ { "text": "4:05" } ] },
              "thumbnail": { "thumbnails": [ { "url": "https://example.invalid/art.jpg" } ] }
            }
            """.trimIndent()
        ).jsonObject

        val track = parsePlaylistPanelVideo(renderer)

        assertNotNull(track)
        assertEquals("ytm:abc123", track!!.id)
        assertEquals("Real Title", track.title)
        assertEquals("Real Artist", track.artist)
        assertEquals("Real Album", track.album)
        assertEquals(245_000L, track.durationMs)
    }
}
