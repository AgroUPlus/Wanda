package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The browse id of a track's Lyrics tab, out of its `next` response, or null when the track has
 * none — a track without lyrics still has the tab, but it is unselectable and carries no endpoint.
 * Lyrics browse ids start `MPLYt`; the Up next and Related tabs point elsewhere.
 */
internal fun JsonObject.lyricsBrowseId(): String? =
    renderers("tabRenderer").firstNotNullOfOrNull { tab ->
        tab.path("endpoint", "browseEndpoint", "browseId").text()
            ?.takeIf { it.startsWith(LYRICS_BROWSE_PREFIX) }
    }

private const val LYRICS_BROWSE_PREFIX = "MPLY"

/**
 * A track's lyrics, out of `browse <MPLYt…>`: timed when the response carries
 * `timedLyricsData` (the app clients' shape), else the plain text of the description shelf.
 *
 * Plain lyrics are handed back as unsynced lines, one per line of text, because that is the shape
 * the lyrics panel and its cache read; `isSynced = false` keeps them from being scrolled in time.
 * Null when the page holds neither, rather than an empty set of lines.
 */
internal fun JsonObject.parseLyrics(trackId: String): LyricsData? {
    val timed = timedLyricLines()
    if (timed.isNotEmpty()) {
        return LyricsData(
            trackId = trackId,
            isSynced = true,
            lines = timed,
            source = LYRICS_SOURCE
        )
    }

    val plain = renderers("musicDescriptionShelfRenderer").firstNotNullOfOrNull { shelf ->
        shelf["description"].path("runs")?.array()
            ?.joinToString("") { it.path("text").text().orEmpty() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    } ?: return null

    return LyricsData(
        trackId = trackId,
        isSynced = false,
        plainLyrics = plain,
        lines = plain.lines().map { LyricLine(timestampMs = 0L, text = it.trim()) },
        source = LYRICS_SOURCE
    )
}

private const val LYRICS_SOURCE = "YouTube Music"

/**
 * `timedLyricsData[]`, wherever it sits — it is buried in a component model several renderers
 * deep — as lines with their start times. A line whose start is missing is skipped rather than
 * pinned to zero, which would show it at the top of the song.
 */
private fun JsonObject.timedLyricLines(): List<LyricLine> =
    firstArray(this, TIMED_LYRICS_KEY).orEmpty().mapNotNull { cue ->
        val text = cue.path("lyricLine").text() ?: return@mapNotNull null
        val start = cue.path("cueRange", "startTimeMilliseconds").text()?.toLongOrNull()
            ?: return@mapNotNull null
        LyricLine(timestampMs = start, text = text.trim())
    }.sortedBy { it.timestampMs }

private const val TIMED_LYRICS_KEY = "timedLyricsData"

private fun firstArray(node: JsonElement, key: String): JsonArray? = when (node) {
    is JsonObject -> (node[key] as? JsonArray)
        ?: node.values.firstNotNullOfOrNull { firstArray(it, key) }
    is JsonArray -> node.firstNotNullOfOrNull { firstArray(it, key) }
    else -> null
}
