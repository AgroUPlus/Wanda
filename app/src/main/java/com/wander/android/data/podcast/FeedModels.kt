package com.wander.android.data.podcast

import java.io.IOException

/** A feed that arrived but is not one this parser can read. */
class FeedParseException(message: String) : IOException(message)

/** A transcript the feed offers for an episode (`<podcast:transcript>`), in whatever [mimeType] it came. */
data class TranscriptLink(val url: String, val mimeType: String?)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val audioUrl: String,
    val mimeType: String?,
    val durationMs: Long,
    val publishedAt: Long?,
    val artworkUrl: String?,
    /** `<podcast:chapters url>`: a Podcasting 2.0 JSON chapters file, fetched only if the listener opens chapters. */
    val chaptersUrl: String?,
    val transcripts: List<TranscriptLink>
)

data class ParsedFeed(
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val episodes: List<ParsedEpisode>
)
