package com.wander.android.data.model

/**
 * A track matching a lyrics search query, accompanied by the matched text line
 * and its playback timestamp offset (if synced lyrics are available).
 */
data class LyricMatch(
    val track: UnifiedTrack,
    val matchedLine: String,
    val snippet: String,
    val timestampMs: Long? = null,
    val source: String = "LRCLIB"
)
