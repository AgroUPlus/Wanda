package com.wander.android

import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.repository.LyricsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParserTest {

    @Test
    fun testLrcParserAccurateTimestamps() {
        val repo = LyricsRepository(emptySet(), HttpClientFactory.ktorClient)
        val sampleLrc = """
            [00:12.50]Line one of song
            [01:04.20]Chorus starts here
            [02:30.00]Final outro
        """.trimIndent()

        val lines = repo.parseLrc(sampleLrc)
        assertEquals(3, lines.size)

        // 00:12.50 -> 12500ms
        assertEquals(12500L, lines[0].timestampMs)
        assertEquals("Line one of song", lines[0].text)

        // 01:04.20 -> 64200ms
        assertEquals(64200L, lines[1].timestampMs)
        assertEquals("Chorus starts here", lines[1].text)

        // 02:30.00 -> 150000ms
        assertEquals(150000L, lines[2].timestampMs)
        assertEquals("Final outro", lines[2].text)
    }
}
