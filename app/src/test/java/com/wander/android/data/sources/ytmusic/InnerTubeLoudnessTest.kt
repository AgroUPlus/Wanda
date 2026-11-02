package com.wander.android.data.sources.ytmusic

import com.wander.android.core.playback.LoudnessHints
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InnerTubeLoudnessTest {

    private fun player(audioConfig: String) =
        Json.parseToJsonElement("""{"playerConfig":{"audioConfig":$audioConfig}}""").jsonObject

    @Test
    fun `a loud upload becomes a negative ReplayGain 2 gain`() {
        // 5.2 dB over YouTube's -14 LUFS is -8.8 LUFS, 9.2 dB above ReplayGain's -18.
        assertEquals(-9.2f, player("""{"loudnessDb":5.2,"perceptualLoudnessDb":-8.8}""").loudnessGainDb()!!, 0.001f)
    }

    @Test
    fun `a quiet upload gains`() {
        assertEquals(2f, player("""{"loudnessDb":-6.0}""").loudnessGainDb()!!, 0.001f)
    }

    @Test
    fun `a response without the field or with a broken one says nothing`() {
        assertNull(player("""{}""").loudnessGainDb())
        assertNull(Json.parseToJsonElement("{}").jsonObject.loudnessGainDb())
        assertNull(player("""{"loudnessDb":"loud"}""").loudnessGainDb())
        assertNull(player("""{"loudnessDb":60}""").loudnessGainDb())
    }

    @Test
    fun `a later resolve without a value clears the hint`() {
        val hints = LoudnessHints()
        hints.record("ytm:a", -3f)
        assertEquals(-3f, hints.forTrack("ytm:a").trackGainDb)
        hints.record("ytm:a", null)
        assertNull(hints.forTrack("ytm:a").trackGainDb)
    }
}
