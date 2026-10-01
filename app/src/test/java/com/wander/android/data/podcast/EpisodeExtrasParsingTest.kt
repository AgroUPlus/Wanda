package com.wander.android.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class EpisodeExtrasParsingTest {

    @Test
    fun `chapters come back in playing order and skip hidden markers`() {
        val chapters = EpisodeChapters.parse(
            """
            {"version":"1.2.0","chapters":[
              {"startTime":120.5,"title":"Second"},
              {"startTime":0,"title":"Intro"},
              {"startTime":60,"title":"Ad slot","toc":false},
              {"startTime":30}
            ]}
            """.trimIndent()
        )

        assertEquals(listOf(Chapter(0, "Intro"), Chapter(120_500, "Second")), chapters)
    }

    @Test
    fun `a chapters file of the wrong shape is an io error, not a crash`() {
        assertThrows(IOException::class.java) { EpisodeChapters.parse("""{"chapters":[1,2]}""") }
        assertThrows(IOException::class.java) { EpisodeChapters.parse("not json") }
        assertThrows(IOException::class.java) { EpisodeChapters.parse("""{"other":[]}""") }
    }

    @Test
    fun `subrip cues`() {
        val srt = "1\n00:00:01,500 --> 00:00:03,000\nHello <i>there</i>\nfriend\n\n2\n01:00:00,000 --> 01:00:02,000\nLate one\n"

        assertEquals(
            listOf(TranscriptCue(1_500, "Hello there friend"), TranscriptCue(3_600_000, "Late one")),
            TranscriptParser.parse(TranscriptParser.Format.SRT, srt)
        )
    }

    @Test
    fun `webvtt cues skip the header, notes and cue settings`() {
        val vtt = "WEBVTT\n\nNOTE a comment\n\nintro\n00:01.000 --> 00:02.000 align:start\n<v Host>Welcome\n\n00:00:05.250 --> 00:00:06.000\nBye\n"

        assertEquals(
            listOf(TranscriptCue(1_000, "Welcome"), TranscriptCue(5_250, "Bye")),
            TranscriptParser.parse(TranscriptParser.Format.VTT, vtt)
        )
    }

    @Test
    fun `podcasting 2 json transcript`() {
        val json = """{"version":"1.0.0","segments":[{"speaker":"A","startTime":2,"endTime":4,"body":" Second "},{"startTime":0.5,"body":"First"}]}"""

        assertEquals(
            listOf(TranscriptCue(500, "First"), TranscriptCue(2_000, "Second")),
            TranscriptParser.parse(TranscriptParser.Format.JSON, json)
        )
    }

    @Test
    fun `the best readable transcript is picked, and html alone is none`() {
        val links = listOf(
            TranscriptLink("https://e.com/t.html", "text/html"),
            TranscriptLink("https://e.com/t.srt", "application/srt"),
            TranscriptLink("https://e.com/t.vtt", "text/vtt; charset=utf-8")
        )

        assertEquals("https://e.com/t.vtt", TranscriptParser.pick(links)?.url)
        assertNull(TranscriptParser.pick(links.take(1)))
    }
}
