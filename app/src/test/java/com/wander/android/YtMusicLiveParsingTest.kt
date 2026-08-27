package com.wander.android

import com.wander.android.data.sources.ytmusic.InnerTubeVariant
import com.wander.android.data.sources.ytmusic.bestAudioFormat
import com.wander.android.data.sources.ytmusic.hlsManifestUrl
import com.wander.android.data.sources.ytmusic.isLiveEntry
import com.wander.android.data.sources.ytmusic.visitorData
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

    /**
     * The shape YouTube Music search actually returns, captured verbatim.
     *
     * Matched on the renderer key. The label beside it is display text — "Live" here, "En direct"
     * on a French device — so matching that instead meant a French user's own search results were
     * never recognised as live, and they played as ordinary tracks with a scrubbable hour-long
     * seek bar.
     */
    @Test
    fun detectsLiveBadgeRendererOnSearchRow() {
        val renderer = obj(
            """
            {
              "badges": [
                {
                  "liveBadgeRenderer": {
                    "label": { "runs": [ { "text": "Live" } ] },
                    "accessibility": { "accessibilityData": { "label": "Live" } }
                  }
                }
              ]
            }
            """
        )
        assertTrue(renderer.isLiveEntry())
    }

    /** The same row as a French device receives it. The key does not translate; the label does. */
    @Test
    fun detectsLiveBadgeRendererWhateverTheLanguage() {
        val renderer = obj(
            """
            {
              "badges": [
                { "liveBadgeRenderer": { "label": { "runs": [ { "text": "En direct" } ] } } }
              ]
            }
            """
        )
        assertTrue(renderer.isLiveEntry())
    }

    /** A badge that is not a live one must not become one just by sitting in `badges`. */
    @Test
    fun otherBadgesAreNotLive() {
        val renderer = obj(
            """
            {
              "badges": [
                { "musicInlineBadgeRenderer": { "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" } } }
              ]
            }
            """
        )
        assertFalse(renderer.isLiveEntry())
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

    /**
     * The visitor session the livestream identity will not go without.
     *
     * Every InnerTube response carries one; `visitor_id` is just the cheapest way to ask. Without
     * it `VISIONOS` answers LOGIN_REQUIRED for every video and no manifest is ever minted.
     */
    @Test
    fun readsVisitorSessionFromResponseContext() {
        val body = obj(
            """
            {
              "responseContext": { "visitorData": "CgtMb3VEMXRZd0dPUSjS5cHUBjIKCgJGUhIEGgAgYg%3D%3D" }
            }
            """
        )
        assertEquals("CgtMb3VEMXRZd0dPUSjS5cHUBjIKCgJGUhIEGgAgYg%3D%3D", body.visitorData())
    }

    @Test
    fun visitorSessionIsNullWhenAbsentOrBlank() {
        assertNull(obj("""{ "responseContext": {} }""").visitorData())
        assertNull(obj("""{ "responseContext": { "visitorData": "" } }""").visitorData())
    }

    /**
     * Pins the livestream identity.
     *
     * Not a style preference: iOS is the one client YouTube requires a PO Token from on live HLS,
     * so it serves a manifest that plays for about thirty seconds and then 403s every further
     * segment — the "Stream expired. Play it again to refresh it." this replaced. Anything moved
     * back here has to be checked against that rule first.
     */
    @Test
    fun livestreamsUseTheVisionOsIdentity() {
        val live = InnerTubeVariant.VISIONOS
        assertEquals("101", live.clientId)
        assertEquals("VISIONOS", live.contextClientName)
        assertEquals("https://www.youtube.com/youtubei/v1", live.apiBaseUrl)
        // The music host answers plain-YouTube identities inconsistently.
        assertTrue(InnerTubeVariant.entries.none { it.contextClientName == "IOS" })
    }
}
