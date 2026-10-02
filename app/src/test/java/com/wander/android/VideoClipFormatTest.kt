package com.wander.android

import com.wander.android.data.model.PlaybackMediaType
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.ytmusic.bestVideoFormat
import com.wander.android.ui.components.player.clipSize
import com.wander.android.ui.screens.player.MediaToggleState
import com.wander.android.ui.screens.player.swapTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoClipFormatTest {

    private fun player(vararg formats: String): JsonObject = Json.parseToJsonElement(
        """{"streamingData":{"adaptiveFormats":[${formats.joinToString(",")}]}}"""
    ).jsonObject

    private fun format(itag: Int, mime: String, height: Int? = null) =
        """{"itag":$itag,"mimeType":"$mime","url":"https://example.invalid/$itag"""" +
            (height?.let { ""","height":$it""" } ?: "") + "}"

    private val avc = """video/mp4; codecs=\"avc1.4d401f\""""
    private val vp9 = """video/webm; codecs=\"vp9\""""
    private val opus = """audio/webm; codecs=\"opus\""""

    @Test
    fun `prefers the tallest H264 within 1080p and ignores audio`() {
        val picked = player(
            format(251, opus),
            format(299, avc, 1440),
            format(137, avc, 1080),
            format(136, avc, 720),
            format(248, vp9, 1080)
        ).bestVideoFormat()
        assertEquals("137", picked?.get("itag")?.toString())
    }

    @Test
    fun `falls back to VP9 then to the smallest oversized stream`() {
        assertEquals("248", player(format(248, vp9, 1080), format(299, avc, 1440)).bestVideoFormat()?.get("itag")?.toString())
        assertEquals("299", player(format(299, avc, 1440), format(400, avc, 2160)).bestVideoFormat()?.get("itag")?.toString())
    }

    @Test
    fun `no video formats means no clip`() {
        assertNull(player(format(251, opus)).bestVideoFormat())
    }

    private fun track(id: String) = UnifiedTrack(id = id, source = SourceType.YTMUSIC, title = "T", artist = "A")

    @Test
    fun `swaps only when the other form is wanted and exists`() {
        val other = track("ytm_v")
        assertEquals(other, swapTarget(MediaToggleState(PlaybackMediaType.SONG, other), PlaybackMediaType.VIDEO))
        assertNull(swapTarget(MediaToggleState(PlaybackMediaType.VIDEO, other), PlaybackMediaType.VIDEO))
        assertNull(swapTarget(MediaToggleState(PlaybackMediaType.VIDEO, null), PlaybackMediaType.SONG))
        assertNull(swapTarget(MediaToggleState(null, other), PlaybackMediaType.VIDEO))
    }

    @Test
    fun `fill covers the box and fit stays inside it`() {
        // 16:9 clip in a square box.
        assertEquals(1778 to 1000, clipSize(1000, 1000, 16f / 9f, fill = true))
        assertEquals(1000 to 563, clipSize(1000, 1000, 16f / 9f, fill = false))
        // 16:9 clip in a tall, screen-shaped box.
        assertEquals(3556 to 2000, clipSize(1000, 2000, 16f / 9f, fill = true))
        assertEquals(1000 to 2000, clipSize(1000, 2000, 0f, fill = true))
    }
}
