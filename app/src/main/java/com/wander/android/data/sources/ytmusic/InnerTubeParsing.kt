package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.UNKNOWN_ARTIST
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

/**
 * Whether a row is a livestream rather than a recording.
 *
 * YouTube marks these three different ways depending on the surface, and a row only has to carry
 * one of them:
 *
 *  - **`liveBadgeRenderer`** — a renderer *key*, which is what YouTube Music search actually uses
 *    today. Matched by name rather than by its label, because that label is display text: it reads
 *    "Live" in English and "En direct" in French, so a French device recognised none of its own
 *    search results. This is why lives found through the search bar played as ordinary tracks.
 *  - **`BADGE_STYLE_TYPE_LIVE_NOW`** — an icon style on plain-YouTube rows.
 *  - **`LIVE`** — the time-status overlay style on queue entries, where a recording prints a
 *    duration.
 *
 * Deliberately still a marker hunt rather than an inference from a missing duration: plenty of
 * ordinary rows have no duration either, and guessing would put a seekless LIVE chip on records.
 */
internal fun JsonObject.isLiveEntry(): Boolean =
    LIVE_MARKER_KEYS.any { key -> this[key]?.let { containsLiveMarker(it) } == true }

private val LIVE_MARKER_KEYS = listOf("badges", "thumbnailOverlays")

/** Renderer names, never localised. */
private const val LIVE_BADGE_RENDERER = "liveBadgeRenderer"

/** Enum-style values, never localised either. Display text is deliberately not matched. */
private val LIVE_MARKERS = setOf("BADGE_STYLE_TYPE_LIVE_NOW", "LIVE")

private fun containsLiveMarker(node: JsonElement): Boolean = when (node) {
    is JsonObject -> node.any { (key, v) -> key == LIVE_BADGE_RENDERER || containsLiveMarker(v) }
    is JsonArray -> node.any { containsLiveMarker(it) }
    else -> node.text() in LIVE_MARKERS
}

/** A search result or playlist row. */
internal fun parseResponsiveListItem(renderer: JsonObject): UnifiedTrack? {
    val columns = renderer["flexColumns"]?.array() ?: return null
    fun column(i: Int) = columns.getOrNull(i)
        .path("musicResponsiveListItemFlexColumnRenderer", "text")

    val title = column(0).runText() ?: return null
    val subtitle = InnerTubeSubtitle.of(column(1).path("runs")?.array())

    val videoId = renderer.path("playlistItemData", "videoId").text()
        ?: renderer.path("navigationEndpoint", "watchEndpoint", "videoId").text()
        ?: return null

    return UnifiedTrack(
        id = "$YTM_PREFIX$videoId",
        source = SourceType.YTMUSIC,
        title = title,
        artist = subtitle.artist ?: UNKNOWN_ARTIST,
        artistId = subtitle.artistId?.let { "$YTM_PREFIX$it" },
        album = subtitle.album,
        artworkUrl = renderer.path("thumbnail", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail(),
        durationMs = parseDurationText(subtitle.duration),
        format = "audio/webm",
        bitRateKbps = 160,
        isLive = renderer.isLiveEntry()
    )
}

/**
 * A radio queue entry. These carry full metadata, which the previous implementation discarded —
 * every radio track showed up as "Radio Track" with a zero duration.
 */
internal fun parsePlaylistPanelVideo(renderer: JsonObject): UnifiedTrack? {
    val videoId = renderer["videoId"].text() ?: return null
    val byline = InnerTubeSubtitle.of(renderer["longBylineText"].path("runs")?.array())

    return UnifiedTrack(
        id = "$YTM_PREFIX$videoId",
        source = SourceType.YTMUSIC,
        title = renderer["title"].runText() ?: return null,
        artist = byline.artist ?: UNKNOWN_ARTIST,
        artistId = byline.artistId?.let { "$YTM_PREFIX$it" },
        album = byline.album,
        artworkUrl = renderer["thumbnail"].bestThumbnail(),
        durationMs = parseDurationText(renderer["lengthText"].runText()),
        format = "audio/webm",
        bitRateKbps = 160,
        isLive = renderer.isLiveEntry()
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
        artist = InnerTubeSubtitle.of(column(1).path("runs")?.array()).artist ?: UNKNOWN_ARTIST,
        coverArtUrl = renderer.path("thumbnail", "musicThumbnailRenderer", "thumbnail")
            .bestThumbnail()
    )
}

/**
 * The signed-in account's display name, out of an `account/account_menu` response.
 *
 * Found by sweeping for the renderer rather than walking the documented path. The menu arrives
 * wrapped in an `actions[].openPopupAction.popup.multiPageMenuRenderer.header` chain that exists
 * to describe a popup, and none of those hops mean anything here — the same recursive search the
 * rest of this file uses for renderers survives YouTube rearranging them, which it does.
 */
internal fun JsonObject.activeAccountName(): String? =
    renderers("activeAccountHeaderRenderer")
        .firstNotNullOfOrNull { it["accountName"].runText() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

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

/**
 * The HLS manifest a livestream is served through.
 *
 * A live video has no `adaptiveFormats` and no `formats` at all, so [bestAudioFormat] finds
 * nothing and the caller used to treat that as "unplayable" — the load failed, and ExoPlayer's
 * default response to a failed item is to advance the queue. That is precisely why live tracks
 * looked like they were being skipped.
 */
internal fun JsonObject.hlsManifestUrl(): String? =
    path("streamingData", "hlsManifestUrl").text()?.takeIf { it.isNotBlank() }

/**
 * The visitor session YouTube issued for this request.
 *
 * Every InnerTube response carries one back in its `responseContext`, so the dedicated
 * `visitor_id` endpoint is only the cheapest way to ask for one — the field is read the same way
 * wherever it turns up. See `InnerTubeClient.visitorSession`.
 */
internal fun JsonObject.visitorData(): String? =
    path("responseContext", "visitorData").text()?.takeIf { it.isNotBlank() }

/** Either a ready-to-fetch `url`, or a cipher `StreamUrlResolver` can turn into one. */
private fun JsonObject.hasPlayableSource(): Boolean =
    !this["url"].text().isNullOrBlank() || !signatureCipher().isNullOrBlank()

/** The obfuscated `s`/`sp`/`url` query blob web clients return in place of a plain `url`. */
internal fun JsonObject.signatureCipher(): String? =
    this["signatureCipher"].text() ?: this["cipher"].text()
