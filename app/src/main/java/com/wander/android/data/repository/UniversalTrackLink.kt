package com.wander.android.data.repository

/**
 * A track described by what it *is*, rather than by where the sender happened to have it.
 *
 * The same problem [UniversalAlbumLink] solves for records exists for tracks: a Navidrome share
 * is a private server address the recipient cannot reach, a YouTube Music link opens YouTube
 * Music, and a local file has no link at all. So a track link carries metadata and no location —
 * the receiving device resolves it against whatever *it* has configured, the same way
 * [UniversalAlbumLink] does for albums.
 *
 * ```
 * wanda://track?title=Everything%20In%20Its%20Right%20Place&artist=Radiohead&album=Kid%20A&duration=249000
 * ```
 *
 * Album and duration are hints for telling two recordings of the same name apart; they are not
 * required, and a link without them still resolves.
 */
internal data class UniversalTrackLink(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null
) {

    fun toUri(): String {
        val parameters = buildList {
            add("title" to title)
            add("artist" to artist)
            album?.takeIf { it.isNotBlank() }?.let { add("album" to it) }
            durationMs?.takeIf { it > 0 }?.let { add("duration" to it.toString()) }
        }
        val query = UniversalLinkCodec.buildQuery(parameters)
        return "$SCHEME://$HOST?$query"
    }

    internal companion object {
        const val SCHEME = "wanda"
        const val HOST = "track"

        private const val PREFIX = "$SCHEME://$HOST"

        fun matches(uri: String): Boolean = uri.trim().startsWith("$PREFIX?")

        /**
         * Reads a link, or null when it is not one or carries too little to act on.
         *
         * A title with no artist is refused rather than resolved loosely, for the same reason
         * [UniversalAlbumLink.parse] refuses one: playing the wrong recording is worse than
         * saying the link was broken.
         */
        fun parse(uri: String): UniversalTrackLink? {
            if (!matches(uri)) return null
            val parameters = UniversalLinkCodec.parseQuery(uri)

            val title = parameters["title"]?.trim().orEmpty()
            val artist = parameters["artist"]?.trim().orEmpty()
            if (title.isEmpty() || artist.isEmpty()) return null
            return UniversalTrackLink(
                title = title,
                artist = artist,
                album = parameters["album"]?.trim()?.takeIf { it.isNotEmpty() },
                durationMs = parameters["duration"]?.toLongOrNull()?.takeIf { it > 0 }
            )
        }
    }
}
