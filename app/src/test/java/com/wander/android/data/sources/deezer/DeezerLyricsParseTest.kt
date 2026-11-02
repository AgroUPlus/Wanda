package com.wander.android.data.sources.deezer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerLyricsParseTest {

    private fun parse(json: String) = DeezerLyrics.parse("deezer:1", Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `timed lines are kept in order and gaps dropped`() {
        val lyrics = parse(
            """{"LYRICS_TEXT":"One\r\nTwo","LYRICS_SYNC_JSON":[
                {"line":"Two","milliseconds":"4200","lrc_timestamp":"[00:04.20]"},
                {"line":""},
                {"line":"One","milliseconds":1000}
            ]}"""
        )!!
        assertTrue(lyrics.isSynced)
        assertEquals(listOf(1000L to "One", 4200L to "Two"), lyrics.lines.map { it.timestampMs to it.text })
        assertEquals("One\nTwo", lyrics.plainLyrics)
    }

    @Test
    fun `plain text alone reads as untimed lines`() {
        val lyrics = parse("""{"LYRICS_TEXT":"One\r\n\r\nTwo"}""")!!
        assertFalse(lyrics.isSynced)
        assertEquals(listOf("One", "Two"), lyrics.lines.map { it.text })
    }

    @Test
    fun `results without words are no lyrics`() {
        assertNull(parse("""{"LYRICS_TEXT":"  ","LYRICS_SYNC_JSON":[]}"""))
    }
}
