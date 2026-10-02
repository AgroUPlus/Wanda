package com.wander.android.data.podcast

import org.xmlpull.v1.XmlPullParser
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads an RSS 2.0 podcast feed, including the iTunes and Podcasting 2.0 extras, with the
 * platform's pull parser, so no XML library is added for it.
 *
 * Namespaces are matched by their conventional prefixes (`itunes:`, `podcast:`) rather than by URI.
 * Every feed in the wild uses those prefixes, and the pull parser's namespace mode would cost more
 * code than the handful of feeds that alias them would ever repay.
 *
 * The caller owns the parser and its input. Entities are never expanded to anything external: the
 * Android parser ignores the DOCTYPE, which is what makes feeds from arbitrary hosts safe to read.
 */
object RssFeedParser {

    private const val CHANNEL_CHILD_DEPTH = 3

    fun parse(parser: XmlPullParser): ParsedFeed {
        var title: String? = null
        var author: String? = null
        // Two ways a feed names its cover, and either can come first. iTunes' is the one current
        // players read and is the larger, so it wins; the plain `<image>` is only the fallback.
        var itunesArtwork: String? = null
        var imageArtwork: String? = null
        val episodes = mutableListOf<ParsedEpisode>()
        var sawChannel = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "channel" -> sawChannel = true
                "item" -> parseItem(parser)?.let(episodes::add)
                "title" -> if (parser.depth == CHANNEL_CHILD_DEPTH && title == null) title = parser.nextText().trim()
                "itunes:author" -> if (parser.depth == CHANNEL_CHILD_DEPTH && author == null) {
                    author = parser.nextText().trim().ifBlank { null }
                }
                "itunes:image" -> if (parser.depth == CHANNEL_CHILD_DEPTH && itunesArtwork == null) {
                    itunesArtwork = parser.getAttributeValue(null, "href")
                }
                "image" -> if (parser.depth == CHANNEL_CHILD_DEPTH && imageArtwork == null) imageArtwork = readImageUrl(parser)
            }
        }
        if (!sawChannel) throw FeedParseException("Not an RSS podcast feed")
        return ParsedFeed(
            title = title?.ifBlank { null } ?: throw FeedParseException("The feed has no title"),
            author = author,
            artworkUrl = itunesArtwork ?: imageArtwork,
            episodes = episodes
        )
    }

    /** `<image><url>…</url></image>`: the pre-iTunes way of naming a cover. */
    private fun readImageUrl(parser: XmlPullParser): String? {
        val depth = parser.depth
        var url: String? = null
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) throw FeedParseException("The feed ends mid-element")
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return url
            if (event == XmlPullParser.START_TAG && parser.name == "url") url = parser.nextText().trim().ifBlank { null }
        }
    }

    /** Null for an item with no audio to play: show notes and announcements share the feed. */
    private fun parseItem(parser: XmlPullParser): ParsedEpisode? {
        val depth = parser.depth
        var title: String? = null
        var guid: String? = null
        var published: Long? = null
        var audio: String? = null
        var mime: String? = null
        var duration = 0L
        var artwork: String? = null
        var chapters: String? = null
        val transcripts = mutableListOf<TranscriptLink>()

        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) throw FeedParseException("The feed ends mid-item")
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event != XmlPullParser.START_TAG || parser.depth != depth + 1) continue
            when (parser.name) {
                "title" -> title = parser.nextText().trim()
                "guid" -> guid = parser.nextText().trim().ifBlank { null }
                "pubDate" -> published = parseDate(parser.nextText())
                "enclosure" -> {
                    audio = parser.getAttributeValue(null, "url")?.trim()?.ifBlank { null }
                    mime = parser.getAttributeValue(null, "type")
                }
                "itunes:duration" -> duration = parseDurationMs(parser.nextText())
                "itunes:image" -> artwork = parser.getAttributeValue(null, "href")
                "podcast:chapters" -> chapters = parser.getAttributeValue(null, "url")
                "podcast:transcript" -> parser.getAttributeValue(null, "url")?.let {
                    transcripts += TranscriptLink(it, parser.getAttributeValue(null, "type"))
                }
            }
        }

        val url = audio ?: return null
        return ParsedEpisode(
            guid = guid ?: url,
            title = title?.ifBlank { null } ?: url,
            audioUrl = url,
            mimeType = mime,
            durationMs = duration,
            publishedAt = published,
            artworkUrl = artwork,
            chaptersUrl = chapters,
            transcripts = transcripts
        )
    }

    /** `HH:MM:SS`, `MM:SS`, or plain seconds, as iTunes writes them. Zero when absent or unreadable. */
    internal fun parseDurationMs(raw: String): Long {
        val parts = raw.trim().split(':')
        if (parts.isEmpty() || parts.size > 3) return 0L
        var seconds = 0.0
        for (part in parts) {
            val value = part.toDoubleOrNull() ?: return 0L
            seconds = seconds * 60 + value
        }
        return (seconds * 1000).toLong()
    }

    /** RFC 822 as feeds write it, falling back to ISO 8601. Null when neither fits. */
    internal fun parseDate(raw: String): Long? {
        val text = raw.trim()
        return try {
            OffsetDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
