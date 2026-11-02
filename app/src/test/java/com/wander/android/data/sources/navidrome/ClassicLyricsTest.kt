package com.wander.android.data.sources.navidrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicLyricsTest {

    @Test
    fun `LRC in the classic text field reads as synced`() {
        val lyrics = NavidromeLyrics.classicLyrics("navidrome:1", "[00:01.00]One\n[00:03.00]Two")
        assertTrue(lyrics.isSynced)
        assertEquals(listOf(1000L, 3000L), lyrics.lines.map { it.timestampMs })
    }

    @Test
    fun `plain classic text reads as untimed lines`() {
        val lyrics = NavidromeLyrics.classicLyrics("navidrome:1", "One\n\nTwo")
        assertFalse(lyrics.isSynced)
        assertEquals(listOf("One", "Two"), lyrics.lines.map { it.text })
        assertEquals("One\n\nTwo", lyrics.plainLyrics)
    }
}
