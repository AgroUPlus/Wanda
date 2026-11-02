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
        return fromMessage(best, exception.errorCodeName)
    }

    /** The mapping itself, apart from the exception it is read from, so it needs no player classes. */
    fun fromMessage(raw: String?, errorCodeName: String): String =
        raw?.asActionableMessage() ?: "Playback failed ($errorCodeName)."

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

        isExpiredStreamMessage(this) ->
            "Stream expired. Play it again to refresh it."

        isConnectivityMessage(this) ->
            "Can't reach the source. Check your connection."

        else -> this
    }

    /**
     * Whether the stream URL was refused as expired: a 403 or 410 from the host serving it. A
     * YouTube stream URL carries an expiry, so a track paused long enough fails like this on resume.
     */
    fun isExpiredStream(exception: PlaybackException): Boolean = exception.messages().any(::isExpiredStreamMessage)

    /**
     * Whether the failure is the network rather than the track — in which case the next track would
     * fail exactly the same way.
     */
    fun isConnectivityFailure(exception: PlaybackException): Boolean =
        exception.errorCode in CONNECTIVITY_ERROR_CODES || exception.messages().any(::isConnectivityMessage)

    fun isExpiredStreamMessage(raw: String): Boolean =
        raw.contains("Response code: 403") || raw.contains("Response code: 410")

    fun isConnectivityMessage(raw: String): Boolean =
        raw.contains("Unable to connect", ignoreCase = true) || raw.contains("UnknownHost", ignoreCase = true)

    /** Every message down the cause chain. Media3 keeps only the direct cause across the session boundary. */
    private fun PlaybackException.messages(): List<String> =
        generateSequence<Throwable>(this) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .mapNotNull { cause -> cause.message?.takeIf { it.isNotBlank() } }
            .toList()

    private val CONNECTIVITY_ERROR_CODES = setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    )

    private const val MAX_CAUSE_DEPTH = 16
}
