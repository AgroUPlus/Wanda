package com.wander.android.core.playback

import androidx.media3.common.PlaybackException

/**
 * Translates low-level Media3 and HTTP playback failures into user-actionable messages.
 *
 * Media3 wraps the real cause in a generic "Source error", so the useful text — the reason a
 * source refused to resolve a stream — is several levels down. Deliberately excludes URLs or
 * headers, as those carry sensitive tokens or credentials.
 */
internal object PlaybackErrorTranslator {

    fun userMessage(exception: PlaybackException): String {
        var cause: Throwable? = exception
        var best: String? = null
        while (cause != null) {
            cause.message?.takeIf { it.isNotBlank() }?.let { best = it }
            cause = cause.cause
        }
        val raw = best ?: return "Playback failed (${exception.errorCodeName})."
        return raw.asActionableMessage()
    }

    private fun String.asActionableMessage(): String = when {
        contains("YouTube Music refused", ignoreCase = true) &&
            (contains("401") || contains("403")) ->
            "Sign in to YouTube Music again — the session expired."

        contains("no playable audio", ignoreCase = true) ->
            "This track isn't playable from YouTube Music."

        // A signature or throttling-nonce transform failing is transient — YouTube rotated its player
        // JS — and retrying picks up the new one, which is very different advice from "unplayable".
        contains("unscramble", ignoreCase = true) ||
            contains("throttling parameter", ignoreCase = true) ->
            "Couldn't prepare the YouTube stream. Try again in a moment."

        contains("will not play this track", ignoreCase = true) ->
            "YouTube Music won't play this track here."

        contains("Response code: 403") || contains("Response code: 410") ->
            "Stream expired. Play it again to refresh it."

        contains("Unable to connect", ignoreCase = true) ||
            contains("UnknownHost", ignoreCase = true) ->
            "Can't reach the source. Check your connection."

        else -> this
    }
}
