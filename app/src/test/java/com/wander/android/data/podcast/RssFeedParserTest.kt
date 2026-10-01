package com.wander.android.data.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

class RssFeedParserTest {

    private fun parserOf(xml: String): XmlPullParser =
        KXmlParser().also { it.setInput(xml.byteInputStream(), null) }

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"
             xmlns:podcast="https://podcastindex.org/namespace/1.0">
          <channel>
            <title>The Show</title>
            <itunes:author>Some Host</itunes:author>
            <image><url>https://example.com/old.png</url><title>ignored</title></image>
            <itunes:image href="https://example.com/cover.jpg"/>
            <item>
              <title>Episode 2</title>
              <guid isPermaLink="false">ep-2</guid>
              <pubDate>Tue, 03 Jun 2025 11:05:30 GMT</pubDate>
              <enclosure url="https://example.com/2.mp3" length="1" type="audio/mpeg"/>
              <itunes:duration>1:02:03</itunes:duration>
              <podcast:chapters url="https://example.com/2.json" type="application/json+chapters"/>
              <podcast:transcript url="https://example.com/2.srt" type="application/srt"/>
              <podcast:transcript url="https://example.com/2.json" type="application/json"/>
            </item>
            <item>
              <title>Announcement with no audio</title>
              <guid>note</guid>
            </item>
            <item>
              <title><![CDATA[Episode 1 & more]]></title>
              <enclosure url="https://example.com/1.mp3" type="audio/mpeg"/>
              <itunes:duration>754</itunes:duration>
              <pubDate>not a date</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `reads the show and its episodes`() {
        val parsed = RssFeedParser.parse(parserOf(feed))

        assertEquals("The Show", parsed.title)
        assertEquals("Some Host", parsed.author)
        assertEquals("https://example.com/cover.jpg", parsed.artworkUrl)
        assertEquals(listOf("Episode 2", "Episode 1 & more"), parsed.episodes.map { it.title })
    }

    @Test
    fun `reads an episode's audio, length, date, chapters and transcripts`() {
        val episode = RssFeedParser.parse(parserOf(feed)).episodes.first()

        assertEquals("ep-2", episode.guid)
        assertEquals("https://example.com/2.mp3", episode.audioUrl)
        assertEquals("audio/mpeg", episode.mimeType)
        assertEquals(3_723_000L, episode.durationMs)
        assertEquals(1_748_948_730_000L, episode.publishedAt)
        assertEquals("https://example.com/2.json", episode.chaptersUrl)
        assertEquals(
            listOf("application/srt", "application/json"),
            episode.transcripts.map { it.mimeType }
        )
    }

    @Test
    fun `an episode without a guid falls back to its audio url, and a bad date to null`() {
        val episode = RssFeedParser.parse(parserOf(feed)).episodes.last()

        assertEquals("https://example.com/1.mp3", episode.guid)
        assertEquals(754_000L, episode.durationMs)
        assertNull(episode.publishedAt)
    }

    @Test
    fun `a channel image without an itunes cover is used`() {
        val xml = """<rss><channel><title>T</title><image><url>https://e.com/i.png</url></image></channel></rss>"""

        assertEquals("https://e.com/i.png", RssFeedParser.parse(parserOf(xml)).artworkUrl)
    }

    @Test
    fun `durations in every iTunes shape`() {
        assertEquals(3_723_000L, RssFeedParser.parseDurationMs("01:02:03"))
        assertEquals(125_000L, RssFeedParser.parseDurationMs("2:05"))
        assertEquals(90_000L, RssFeedParser.parseDurationMs("90"))
        assertEquals(0L, RssFeedParser.parseDurationMs("soon"))
        assertEquals(0L, RssFeedParser.parseDurationMs(""))
    }

    @Test
    fun `a page that is not a feed is refused`() {
        assertThrows(FeedParseException::class.java) {
            RssFeedParser.parse(parserOf("<html><body>nope</body></html>"))
        }
    }

    @Test
    fun `a feed cut off mid item is refused rather than looped over`() {
        val truncated = "<rss><channel><title>T</title><item><title>x</title>"

        val error = runCatching { RssFeedParser.parse(parserOf(truncated)) }.exceptionOrNull()

        assertTrue(error != null)
    }
}
