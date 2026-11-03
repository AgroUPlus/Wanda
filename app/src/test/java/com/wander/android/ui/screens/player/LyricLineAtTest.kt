package com.wander.android.ui.screens.player

import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.LyricsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Which lyric line is at time T", as the lyrics view and the seek bar's scrub bubble both ask it. */
class LyricLineAtTest {

    private val lines = listOf(
        LyricLine(timestampMs = 1_000L, text = "First verse"),
        LyricLine(timestampMs = 5_000L, text = "  The chorus  "),
        LyricLine(timestampMs = 9_000L, text = ""),
        LyricLine(timestampMs = 12_000L, text = "Last line")
    )

    private fun synced(lines: List<LyricLine>) =
        LyricsState.Present(LyricsData(trackId = "t", isSynced = true, lines = lines))

    @Test
    fun `before the first line there is no line`() {
        assertEquals(-1, lyricLineIndexAt(lines, songTimeMs = 999L))
    }

    @Test
    fun `a line starts exactly at its timestamp and lasts until the next`() {
        assertEquals(0, lyricLineIndexAt(lines, songTimeMs = 1_000L))
        assertEquals(0, lyricLineIndexAt(lines, songTimeMs = 4_999L))
        assertEquals(1, lyricLineIndexAt(lines, songTimeMs = 5_000L))
        assertEquals(3, lyricLineIndexAt(lines, songTimeMs = 600_000L))
    }

    @Test
    fun `the per-track offset shifts lyric time against song time`() {
        // Song time = lyric time + offset, so a +2s correction means the chorus is sung at 7s.
        assertEquals(0, lyricLineIndexAt(lines, songTimeMs = 6_999L, offsetMs = 2_000L))
        assertEquals(1, lyricLineIndexAt(lines, songTimeMs = 7_000L, offsetMs = 2_000L))
        assertEquals(1, lyricLineIndexAt(lines, songTimeMs = 3_000L, offsetMs = -2_000L))
    }

    @Test
    fun `the lead lights a line that much early`() {
        assertEquals(1, lyricLineIndexAt(lines, songTimeMs = 4_880L, leadMs = 120L))
        assertEquals(0, lyricLineIndexAt(lines, songTimeMs = 4_879L, leadMs = 120L))
    }

    @Test
    fun `the bubble text is the trimmed line at the position`() {
        assertEquals("The chorus", syncedLyricTextAt(synced(lines), songTimeMs = 6_000L))
        assertEquals("The chorus", syncedLyricTextAt(synced(lines), songTimeMs = 8_000L, offsetMs = 3_000L))
    }

    @Test
    fun `blank lines and the intro show no words`() {
        assertNull(syncedLyricTextAt(synced(lines), songTimeMs = 10_000L))
        assertNull(syncedLyricTextAt(synced(lines), songTimeMs = 500L))
    }

    @Test
    fun `unsynced or missing lyrics show no words`() {
        val plain = LyricsState.Present(LyricsData(trackId = "t", isSynced = false, plainLyrics = "words"))
        assertNull(syncedLyricTextAt(plain, songTimeMs = 6_000L))
        assertNull(syncedLyricTextAt(LyricsState.Loading, songTimeMs = 6_000L))
        assertNull(syncedLyricTextAt(LyricsState.Absent, songTimeMs = 6_000L))
        assertNull(syncedLyricTextAt(synced(emptyList()), songTimeMs = 6_000L))
    }
}
