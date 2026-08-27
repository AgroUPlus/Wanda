package com.wander.android

import com.wander.android.data.sources.ytmusic.bestAudioFormat
import com.wander.android.data.sources.ytmusic.hlsManifestUrl
import com.wander.android.data.sources.ytmusic.isLiveEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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

    /**
     * The order the two are read in is the whole fix.
     *
     * YouTube answers a livestream with a refusing `playabilityStatus` *and* a usable manifest in
     * the same response. Asking for a format list first turns that into a thrown
     * "will not play this track" and the manifest is never looked at — which is what every
     * livestream did until the caller learned to check for one first.
     */
    @Test
    fun manifestSurvivesARefusingPlayabilityStatus() {
        val body = obj(
            """
            {
              "playabilityStatus": { "status": "LIVE_STREAM_OFFLINE", "reason": "Starting soon" },
              "streamingData": { "hlsManifestUrl": "https://example.test/live.m3u8" }
            }
            """
        )
        assertEquals("https://example.test/live.m3u8", body.hlsManifestUrl())
        // And the format path, if it were reached, would still refuse — so the caller must not
        // reach it.
        assertThrows(IOException::class.java) { body.bestAudioFormat() }
    }
}
