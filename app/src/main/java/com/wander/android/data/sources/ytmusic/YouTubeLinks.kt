package com.wander.android.data.sources.ytmusic

import android.net.Uri

/**
 * The video id inside a YouTube or YouTube Music link, or null if there isn't one.
 *
 * Only the forms that actually identify a single video are accepted. A channel, playlist or
 * search URL has no track behind it, and guessing one from the first thing on the page would open
 * something the user did not tap.
 */
internal fun youTubeVideoId(uri: Uri): String? =
    youTubeVideoId(
        host = uri.host,
        pathSegments = uri.pathSegments.orEmpty(),
        videoQueryParam = { runCatching { uri.getQueryParameter(it) }.getOrNull() }
    )

/**
 * The same rule, over the parts of a URL rather than over [Uri].
 *
 * Split out for the reason `UniversalAlbumLink` is hand-rolled: `Uri` is an Android class with no
 * behaviour on the JVM, so anything that touches it can only be tested on a device. The parsing is
 * the part worth testing — which path segments name a video and which do not is exactly the sort
 * of list that gains an entry and loses one silently.
 */
internal fun youTubeVideoId(
    host: String?,
    pathSegments: List<String>,
    videoQueryParam: (String) -> String?
): String? {
    val normalised = host?.removePrefix("www.")?.removePrefix("m.")?.lowercase() ?: return null
    val id = when (normalised) {
        // youtu.be/<id>
        "youtu.be" -> pathSegments.firstOrNull()

        "youtube.com", "music.youtube.com" -> when (pathSegments.firstOrNull()) {
            // /watch?v=<id> — the canonical form, and what the share sheet produces for a record.
            "watch" -> videoQueryParam("v")
            // /shorts/<id> and /embed/<id> put it in the path instead. `live` belongs with them:
            // it is what YouTube's own share sheet gives for a *broadcast*, and leaving it out
            // meant the manifest claimed the URL — the youtube.com filter carries no path
            // restriction — so Wanda offered itself in the chooser and then answered "that isn't
            // a track Wanda can open", for the one link form a livestream is usually shared as.
            "shorts", "embed", "v", "live" -> pathSegments.getOrNull(1)
            else -> null
        }

        else -> return null
    }

    // YouTube ids are a fixed 11 characters of URL-safe base64. Checking that is what stops a
    // truncated or decorated path segment from becoming a request that can only 404 — and what
    // keeps `/live` itself, with no id after it, from being treated as one.
    return id?.takeIf { it.length == VIDEO_ID_LENGTH && it.all(Char::isVideoIdChar) }
}

private const val VIDEO_ID_LENGTH = 11

private fun Char.isVideoIdChar(): Boolean = isLetterOrDigit() || this == '-' || this == '_'
