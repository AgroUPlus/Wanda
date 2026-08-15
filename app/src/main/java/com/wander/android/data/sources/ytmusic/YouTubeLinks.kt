package com.wander.android.data.sources.ytmusic

import android.net.Uri

/**
 * The video id inside a YouTube or YouTube Music link, or null if there isn't one.
 *
 * Only the forms that actually identify a single video are accepted. A channel, playlist or
 * search URL has no track behind it, and guessing one from the first thing on the page would open
 * something the user did not tap.
 */
internal fun youTubeVideoId(uri: Uri): String? {
    val host = uri.host?.removePrefix("www.")?.removePrefix("m.")?.lowercase() ?: return null
    val id = when (host) {
        // youtu.be/<id>
        "youtu.be" -> uri.pathSegments.firstOrNull()

        "youtube.com", "music.youtube.com" -> when (uri.pathSegments.firstOrNull()) {
            // /watch?v=<id> — the canonical form, and what the share sheet produces.
            "watch" -> uri.getQueryParameter("v")
            // /shorts/<id> and /embed/<id> put it in the path instead.
            "shorts", "embed", "v" -> uri.pathSegments.getOrNull(1)
            else -> null
        }

        else -> return null
    }

    // YouTube ids are a fixed 11 characters of URL-safe base64. Checking that is what stops a
    // truncated or decorated path segment from becoming a request that can only 404.
    return id?.takeIf { it.length == VIDEO_ID_LENGTH && it.all(Char::isVideoIdChar) }
}

private const val VIDEO_ID_LENGTH = 11

private fun Char.isVideoIdChar(): Boolean = isLetterOrDigit() || this == '-' || this == '_'
