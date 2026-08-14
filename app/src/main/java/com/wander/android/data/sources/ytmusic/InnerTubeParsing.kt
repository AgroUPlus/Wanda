package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

internal const val YTM_PREFIX = "ytm:"

/** Walks a chain of object keys, returning null as soon as one is missing. */
private fun JsonElement?.path(vararg keys: String): JsonElement? =
    keys.fold(this) { node, key -> (node as? JsonObject)?.get(key) }

private fun JsonElement?.array(): JsonArray? = this as? JsonArray

private fun JsonElement?.text(): String? = this?.jsonPrimitive?.contentOrNull

/** First `runs[i].text` of a `{ runs: [...] }` node. */
private fun JsonElement?.runText(index: Int = 0): String? =
    path("runs")?.array()?.getOrNull(index).path("text").text()

private fun JsonElement?.bestThumbnail(): String? =
    path("thumbnail", "thumbnails")?.array()?.lastOrNull().path("url").text()

/** `mm:ss` or `h:mm:ss` as printed by YouTube Music. */
internal fun parseDurationText(value: String?): Long {
    val parts = value?.split(':')?.mapNotNull { it.trim().toLongOrNull() } ?: return 0L
    if (parts.isEmpty()) return 0L
    return parts.fold(0L) { acc, part -> acc * 60 + part } * 1000L
}

/** A search result or playlist row. */
internal fun parseResponsiveListItem(renderer: JsonObject): UnifiedTrack? {
    val columns = renderer["flexColumns"]?.array() ?: return null
    fun column(i: Int) = columns.getOrNull(i)
        .path("musicResponsiveListItemFlexColumnRenderer", "text")

    val title = column(0).runText() ?: return null
    val subtitleRuns = column(1).path("runs")?.array()
    val artist = subtitleRuns?.getOrNull(0).path("text").text() ?: "Unknown Artist"
    // Runs are separated by " • " literals, so album and duration sit at odd offsets.
    val album = subtitleRuns?.getOrNull(2).path("text").text()
    val duration = subtitleRuns?.lastOrNull().path("text").text()

    val videoId = renderer.path("playlistItemData", "videoId").text()
        ?: renderer.path("navigationEndpoint", "watchEndpoint", "videoId").text()
        ?: return null

    return UnifiedTrack(
        id = "$YTM_PREFIX$videoId",
        source = SourceType.YTMUSIC,
        title = title,
        artist = artist,
        album = album?.takeUnless { it.contains(':') },
        artworkUrl = renderer.path("thumbnail", "musicThumbnailRenderer").bestThumbnail(),
        durationMs = parseDurationText(duration),
        format = "audio/webm",
        bitRateKbps = 160
    )
}

/**
 * A radio queue entry. These carry full metadata, which the previous implementation discarded —
 * every radio track showed up as "Radio Track" with a zero duration.
 */
internal fun parsePlaylistPanelVideo(renderer: JsonObject): UnifiedTrack? {
    val videoId = renderer["videoId"].text() ?: return null
    val bylineRuns = renderer["longBylineText"].path("runs")?.array()

    return UnifiedTrack(
        id = "$YTM_PREFIX$videoId",
        source = SourceType.YTMUSIC,
        title = renderer["title"].runText() ?: return null,
        artist = bylineRuns?.getOrNull(0).path("text").text() ?: "Unknown Artist",
        album = bylineRuns?.getOrNull(2).path("text").text(),
        artworkUrl = renderer["thumbnail"].bestThumbnail(),
        durationMs = parseDurationText(renderer["lengthText"].runText()),
        format = "audio/webm",
        bitRateKbps = 160
    )
}

/** A library album row: the same renderer shape, but the endpoint points at a browse id. */
internal fun parseLibraryAlbum(renderer: JsonObject): UnifiedAlbum? {
    val columns = renderer["flexColumns"]?.array() ?: return null
    fun column(i: Int) = columns.getOrNull(i)
        .path("musicResponsiveListItemFlexColumnRenderer", "text")

    val browseId = renderer.path("navigationEndpoint", "browseEndpoint", "browseId").text()
        ?: return null
    val title = column(0).runText() ?: return null

    return UnifiedAlbum(
        id = "$YTM_PREFIX$browseId",
        source = SourceType.YTMUSIC,
        title = title,
        artist = column(1).path("runs")?.array()?.getOrNull(2).path("text").text()
            ?: "Unknown Artist",
        coverArtUrl = renderer.path("thumbnail", "musicThumbnailRenderer").bestThumbnail()
    )
}

/** Collects every `musicResponsiveListItemRenderer` anywhere in a browse/search response. */
internal fun JsonObject.responsiveListItems(): List<JsonObject> =
    buildList { collectRenderers("musicResponsiveListItemRenderer", this@responsiveListItems, this) }

internal fun JsonObject.playlistPanelVideos(): List<JsonObject> =
    buildList { collectRenderers("playlistPanelVideoRenderer", this@playlistPanelVideos, this) }

/**
 * InnerTube nests the same renderer under many different shapes depending on the surface, so a
 * recursive sweep is more robust than hard-coding one path per endpoint.
 */
private fun collectRenderers(key: String, node: JsonElement, into: MutableList<JsonObject>) {
    when (node) {
        is JsonObject -> node.forEach { (k, v) ->
            if (k == key && v is JsonObject) into += v else collectRenderers(key, v, into)
        }
        is JsonArray -> node.forEach { collectRenderers(key, it, into) }
        else -> Unit
    }
}

/**
 * Extracts the best audio stream URL from a `/player` response.
 *
 * Throws rather than returning null when the response explains itself, so the failure reaches the
 * user as a cause instead of a generic "no audio". Two cases matter:
 *
 * - `playabilityStatus` is not OK — the video is blocked, private, or the request was challenged.
 * - The formats carry `signatureCipher` instead of `url` — those require running YouTube's
 *   obfuscated JS player to recover the URL. Wanda deliberately does not ship a JS engine, so
 *   this is a genuine limitation and says so rather than looking like an empty result.
 */
internal fun JsonObject.bestAudioStreamUrl(): String? {
    val status = path("playabilityStatus", "status").text()
    if (status != null && status != "OK") {
        // `errorScreen` is an object, so reading it as a primitive always yielded null and the
        // user saw a bare status code. The human-readable text is nested inside its renderer.
        val reason = path("playabilityStatus", "reason").text()
            ?: path(
                "playabilityStatus", "errorScreen", "playerErrorMessageRenderer", "reason"
            ).runText()
            ?: path(
                "playabilityStatus", "errorScreen", "playerErrorMessageRenderer", "subreason"
            ).runText()
        throw IOException("YouTube Music will not play this track: $status${reason?.let { " ($it)" }.orEmpty()}")
    }

    val adaptive = path("streamingData", "adaptiveFormats")?.array()?.map { it.jsonObject }.orEmpty()
    val progressive = path("streamingData", "formats")?.array()?.map { it.jsonObject }.orEmpty()
    val audio = (adaptive + progressive).filter {
        it["mimeType"].text()?.startsWith("audio/") == true
    }
    if (audio.isEmpty()) return null

    // itag 251 is Opus ~160 kbps: the best quality-per-byte YouTube Music offers.
    val best = audio.firstOrNull { it["itag"].text() == "251" }
        ?: audio.maxByOrNull { it["bitrate"].text()?.toLongOrNull() ?: 0L }

    best?.get("url").text()?.let { return it }

    if (audio.any { it["signatureCipher"] != null || it["cipher"] != null }) {
        throw IOException(
            "This track's audio is signature-protected. Wanda does not run YouTube's JS player, " +
                "so it cannot be decoded."
        )
    }
    return null
}
