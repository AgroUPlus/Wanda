package com.wander.android

import com.wander.android.data.sources.ytmusic.hlsManifestUrl
import com.wander.android.data.sources.ytmusic.isLiveEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Livestreams used to be treated as ordinary tracks, fail to load and hand the queue on to the
 * next item — which looked like Wanda skipping them on purpose. These pin down the two markers
 * that identify one and the manifest that plays it.
 */
class YtMusicLiveParsingTest {

    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun detectsLiveBadgeOnSearchRow() {
        val renderer = obj(
            """
            {
              "badges": [
                { "musicInlineBadgeRenderer": { "icon": { "iconType": "BADGE_STYLE_TYPE_LIVE_NOW" } } }
              ]
            }
            """
        )
        assertTrue(renderer.isLiveEntry())
    }

    @Test
    fun detectsLiveOverlayOnQueueEntry() {
        val renderer = obj(
            """
            {
              "thumbnailOverlays": [
                { "thumbnailOverlayTimeStatusRenderer": { "style": "LIVE" } }
              ]
            }
            """
        )
        assertTrue(renderer.isLiveEntry())
    }

    /** A recording carries a duration overlay, not a live one — and must keep its seek bar. */
    @Test
    fun recordingIsNotLive() {
        val renderer = obj(
            """
            {
              "thumbnailOverlays": [
                {
                  "thumbnailOverlayTimeStatusRenderer": {
                    "style": "DEFAULT",
                    "text": { "runs": [ { "text": "3:41" } ] }
                  }
                }
              ]
            }
            """
        )
        assertFalse(renderer.isLiveEntry())
    }

    /** The marker only counts where YouTube actually puts it. A song titled "LIVE" is not one. */
    @Test
    fun titleContainingLiveIsNotLive() {
        val renderer = obj("""{ "title": { "runs": [ { "text": "LIVE" } ] } }""")
        assertFalse(renderer.isLiveEntry())
    }

    @Test
    fun readsHlsManifest() {
        val body = obj(
            """
            { "streamingData": { "hlsManifestUrl": "https://example.test/manifest.m3u8" } }
            """
        )
        assertEquals("https://example.test/manifest.m3u8", body.hlsManifestUrl())
    }

    @Test
    fun noManifestForAnOrdinaryTrack() {
        val body = obj("""{ "streamingData": { "adaptiveFormats": [] } }""")
        assertNull(body.hlsManifestUrl())
    }
}
