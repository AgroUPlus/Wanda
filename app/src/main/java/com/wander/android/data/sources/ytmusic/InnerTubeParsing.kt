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
internal fun JsonElement?.path(vararg keys: String): JsonElement? =
    keys.fold(this) { node, key -> (node as? JsonObject)?.get(key) }

internal fun JsonElement?.array(): JsonArray? = this as? JsonArray

internal fun JsonElement?.text(): String? = this?.jsonPrimitive?.contentOrNull

/** First `runs[i].text` of a `{ runs: [...] }` node. */
internal fun JsonElement?.runText(index: Int = 0): String? =
    path("runs")?.array()?.getOrNull(index).path("text").text()

/**
 * Picks a cover URL out of a node that **holds** a `thumbnails` array — so the caller walks to
 * whichever wrapper its renderer uses (`musicThumbnailRenderer.thumbnail`,
 * `thumbnailRenderer.musicThumbnailRenderer.thumbnail`, or a bare `thumbnail`) and this does the
 * rest. It used to walk one extra `thumbnail` hop itself, which was right for exactly one of
 * those shapes: every radio and queue entry, which passes a bare `thumbnail`, resolved to null
 * and rendered with no cover at all.
 *
 * YouTube's thumbnail list tops out around 544 px, and search rows often carry nothing bigger
 * than 120 px — visibly soft as a full-screen player cover. On the `=w120-h120-l90-rj` host the
 * size lives in the URL, so asking for a larger one is a rewrite rather than another request.
 */
internal fun JsonElement?.bestThumbnail(): String? =
    path("thumbnails")?.array()?.lastOrNull().path("url").text()?.atHighestResolution()

/**
 * Only the sized host is rewritten. `/hqdefault.jpg` → `/maxresdefault.jpg` used to be here too,
 * but `maxresdefault` only exists for videos uploaded above 720p — for everything else it 404s,
 * which is the second reason radio covers came back blank.
 */
private fun String.atHighestResolution(): String =
    if (contains("=w") && contains("-h")) {
        replace(Regex("=w\\d+-h\\d+"), "=w$THUMBNAIL_PX-h$THUMBNAIL_PX")
    } else {
        this
    }

/** Large enough for a full-screen cover at 3× density without asking for more than YouTube has. */
private const val THUMBNAIL_PX = 1080

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
        artworkUrl = renderer.path("thumbnail", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail(),
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
        coverArtUrl = renderer.path("thumbnail", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail()
    )
}

/** Collects every `musicResponsiveListItemRenderer` anywhere in a browse/search response. */
internal fun JsonObject.responsiveListItems(): List<JsonObject> =
    renderers("musicResponsiveListItemRenderer")

internal fun JsonObject.playlistPanelVideos(): List<JsonObject> =
    renderers("playlistPanelVideoRenderer")

/** Every renderer of one kind, anywhere beneath this node, in document order. */
internal fun JsonElement.renderers(key: String): List<JsonObject> =
    buildList { collectRenderers(key, this@renderers, this) }

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
 * - The formats carry `signatureCipher` instead of `url`. Those are still usable — `StreamUrlResolver`
 *   unscrambles them — but a format carrying *neither* is not, and returning one would surface far
 *   downstream as a bare "no playable audio" instead of falling back to the next client variant.
 */
internal fun JsonObject.bestAudioFormat(): JsonObject? {
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
        it["mimeType"].text()?.startsWith("audio/") == true && it.hasPlayableSource()
    }

    // itag 251 is Opus ~160 kbps: the best quality-per-byte YouTube Music offers.
    return audio.firstOrNull { it["itag"].text() == "251" }
        ?: audio.maxByOrNull { it["bitrate"].text()?.toLongOrNull() ?: 0L }
}

/** Either a ready-to-fetch `url`, or a cipher `StreamUrlResolver` can turn into one. */
private fun JsonObject.hasPlayableSource(): Boolean =
    !this["url"].text().isNullOrBlank() || !signatureCipher().isNullOrBlank()

/** The obfuscated `s`/`sp`/`url` query blob web clients return in place of a plain `url`. */
internal fun JsonObject.signatureCipher(): String? =
    this["signatureCipher"].text() ?: this["cipher"].text()
