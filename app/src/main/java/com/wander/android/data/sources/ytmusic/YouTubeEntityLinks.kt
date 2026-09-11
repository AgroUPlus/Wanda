package com.wander.android.data.sources.ytmusic

import android.net.Uri

/** What a YouTube link points at, when it does not point at a single video. */
internal enum class YouTubeEntityKind { ALBUM, PLAYLIST, ARTIST }

/** A record, a playlist or an artist named by a link, with the id its source knows it by. */
internal data class YouTubeEntity(val kind: YouTubeEntityKind, val id: String)

/**
 * The record, playlist or artist a YouTube link points at, or null if it points at neither.
 *
 * The companion to [youTubeVideoId], which deliberately accepts only the forms that name one video.
 * Everything it rejects used to fall through to "that link isn't a track Wanda can open" — including
 * the two things people share most after a song, a record and a playlist. Those are openable; they
 * are simply not tracks, and the dispatch had no third answer.
 *
 * A video always wins where both are present: `watch?v=…&list=…` is a song *in* a playlist, and the
 * thing tapped was the song. Callers check [youTubeVideoId] first for that reason.
 */
internal fun youTubeEntity(uri: Uri): YouTubeEntity? =
    youTubeEntity(
        host = uri.host,
        pathSegments = uri.pathSegments.orEmpty(),
        queryParam = { runCatching { uri.getQueryParameter(it) }.getOrNull() }
    )

/**
 * The same rule, over the parts of a URL rather than over [Uri].
 *
 * Split for the reason [youTubeVideoId] is: `Uri` is an Android class with no behaviour on the JVM,
 * so anything touching it can only be tested on a device, and which path segments name what is
 * exactly the sort of list that gains an entry and loses one silently.
 */
internal fun youTubeEntity(
    host: String?,
    pathSegments: List<String>,
    queryParam: (String) -> String?
): YouTubeEntity? {
    val normalised = host?.removePrefix("www.")?.removePrefix("m.")?.lowercase() ?: return null
    // youtu.be only ever shortens a video. A bare id there is a track or nothing.
    if (normalised != "youtube.com" && normalised != "music.youtube.com") return null

    // A song inside a playlist is a song. Left to `youTubeVideoId`, which the caller asks first.
    if (pathSegments.firstOrNull() == "watch") return null

    return when (pathSegments.firstOrNull()) {
        "playlist" -> queryParam("list")?.let { entity(YouTubeEntityKind.PLAYLIST, it) }

        // YouTube Music puts both records and artists behind `/browse/`, told apart by the prefix
        // the id itself carries — `MPREb_` for a release, `UC` for a channel.
        "browse" -> pathSegments.getOrNull(1)?.let { id ->
            when {
                id.startsWith(ALBUM_BROWSE_PREFIX) -> entity(YouTubeEntityKind.ALBUM, id)
                id.startsWith(CHANNEL_PREFIX) -> entity(YouTubeEntityKind.ARTIST, id)
                else -> null
            }
        }

        "channel" -> pathSegments.getOrNull(1)
            ?.takeIf { it.startsWith(CHANNEL_PREFIX) }
            ?.let { entity(YouTubeEntityKind.ARTIST, it) }

        // `/@handle` is deliberately not accepted. A handle is not a channel id, and the only way
        // to turn one into the other is to ask YouTube — which makes it a network call on a path
        // whose whole job is to decide, cheaply, whether this link is ours to open at all.
        else -> null
    }
}

private fun entity(kind: YouTubeEntityKind, id: String): YouTubeEntity? =
    id.takeIf { it.isNotBlank() && it.all(Char::isEntityIdChar) }
        ?.let { YouTubeEntity(kind, it) }

/** YouTube Music's release browse ids. */
private const val ALBUM_BROWSE_PREFIX = "MPREb_"

/** Channel ids, for both an artist page and an artist browse id. */
private const val CHANNEL_PREFIX = "UC"

private fun Char.isEntityIdChar(): Boolean = isLetterOrDigit() || this == '-' || this == '_'
