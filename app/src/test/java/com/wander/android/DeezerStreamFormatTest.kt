package com.wander.android

import com.wander.android.data.sources.deezer.DeezerAccountTier
import com.wander.android.data.sources.deezer.DeezerAudioFormat
import com.wander.android.data.sources.deezer.DeezerGateway
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerStreamFormatTest {

    @Test
    fun `a HiFi account asking for the best falls back through every format`() {
        assertEquals(
            listOf(DeezerAudioFormat.FLAC, DeezerAudioFormat.MP3_320, DeezerAudioFormat.MP3_128),
            DeezerAudioFormat.ladderFrom(DeezerAccountTier.HIFI, "AUTO")
        )
    }

    @Test
    fun `a premium account set to FLAC gets its own best rather than the free format`() {
        assertEquals(
            listOf(DeezerAudioFormat.MP3_320, DeezerAudioFormat.MP3_128),
            DeezerAudioFormat.ladderFrom(DeezerAccountTier.PREMIUM, "FLAC")
        )
    }

    @Test
    fun `a free account only asks for the free format`() {
        assertEquals(
            listOf(DeezerAudioFormat.MP3_128),
            DeezerAudioFormat.ladderFrom(DeezerAccountTier.FREE, "AUTO")
        )
    }

    @Test
    fun `a preference below the tier caps the ladder`() {
        assertEquals(
            listOf(DeezerAudioFormat.MP3_128),
            DeezerAudioFormat.ladderFrom(DeezerAccountTier.HIFI, "MP3_128")
        )
    }

    @Test
    fun `the gateway's empty error array is not a refusal`() {
        assertTrue(DeezerGateway.errorCodes(Json.parseToJsonElement("[]")).isEmpty())
        assertTrue(DeezerGateway.errorCodes(null).isEmpty())
    }

    @Test
    fun `the gateway's error object is read as its codes`() {
        val error = Json.parseToJsonElement("""{"VALID_TOKEN_REQUIRED":"Invalid CSRF token"}""")
        assertEquals(setOf("VALID_TOKEN_REQUIRED"), DeezerGateway.errorCodes(error))
    }
}
