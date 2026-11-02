package com.wander.android.data.sources.ytmusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeLyricsParsingTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun findsTheLyricsTabAmongTheWatchTabs() {
        val next = json(
            """{ "tabs": [
                 { "tabRenderer": { "title": "Up next" } },
                 { "tabRenderer": { "endpoint": { "browseEndpoint": { "browseId": "MPLYt_abc" } } } },
                 { "tabRenderer": { "endpoint": { "browseEndpoint": { "browseId": "MPTRt_rel" } } } } ] }"""
        )
        assertEquals("MPLYt_abc", next.lyricsBrowseId())
    }

    /** A track without lyrics keeps the tab, disabled and pointing nowhere. */
    @Test
    fun aDisabledLyricsTabMeansNoLyrics() {
        val next = json("""{ "tabs": [ { "tabRenderer": { "unselectable": true, "title": "Lyrics" } } ] }""")
        assertNull(next.lyricsBrowseId())
    }

    @Test
    fun readsPlainLyricsAsUnsyncedLines() {
        val page = json(
            """{ "contents": { "sectionListRenderer": { "contents": [ { "musicDescriptionShelfRenderer": {
                 "description": { "runs": [ { "text": "First line\nSecond line" } ] },
                 "footer": { "runs": [ { "text": "Source: LyricFind" } ] } } } ] } } }"""
        )
        val lyrics = page.parseLyrics("ytm:v1")!!
        assertFalse(lyrics.isSynced)
        assertEquals("First line\nSecond line", lyrics.plainLyrics)
        assertEquals(listOf("First line", "Second line"), lyrics.lines.map { it.text })
    }

    @Test
    fun prefersTimedLyricsWhenPresent() {
        val page = json(
            """{ "contents": { "elementRenderer": { "newElement": { "type": { "componentType": {
                 "model": { "timedLyricsModel": { "lyricsData": { "timedLyricsData": [
                   { "lyricLine": "Second", "cueRange": { "startTimeMilliseconds": "2500" } },
                   { "lyricLine": "First", "cueRange": { "startTimeMilliseconds": "1000" } },
                   { "lyricLine": "No cue" } ] } } } } } } } } }"""
        )
        val lyrics = page.parseLyrics("ytm:v1")!!
        assertTrue(lyrics.isSynced)
        assertEquals(listOf(1000L, 2500L), lyrics.lines.map { it.timestampMs })
        assertEquals("First", lyrics.lines.first().text)
    }

    @Test
    fun aPageWithNoLyricsIsNull() {
        assertNull(json("""{ "contents": {} }""").parseLyrics("ytm:v1"))
    }
}
