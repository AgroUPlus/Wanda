package com.wander.android.data.repository

/**
 * An album described by what it *is*, rather than by where the sender happened to have it.
 *
 * Every other link in the app is a backend's own: Navidrome mints a URL on its own public address,
 * YouTube Music has its own. Those work perfectly for a track, and not at all for the thing people
 * actually do with albums — send one to a friend. A Navidrome link is a private address the
 * recipient cannot reach, and a YouTube Music link opens YouTube Music. Either way, the person who
 * has the record on a shelf and the person who has it in a subscription cannot hand it to each
 * other, which is the one job a share link has.
 *
 * So an album link carries metadata and no location. The receiving device resolves it against
 * whatever *it* has configured — see [AlbumResolution] — and the same link works for someone with
 * a home server, someone streaming and someone with the files on their phone.
 *
 * ```
 * wanda://album?title=Kid%20A&artist=Radiohead&year=2000&tracks=10
 * ```
 *
 * The year and track count are hints for telling two records of one name apart; they are not
 * required, and a link without them still resolves. Nothing here identifies the sender: a share
 * link is forwarded on to people the sender never chose, so it carries the album and nothing else.
 */
internal data class UniversalAlbumLink(
    val title: String,
    val artist: String,
    val year: Int? = null,
    val trackCount: Int? = null
) {

    fun toUri(): String {
        val parameters = buildList {
            add("title" to title)
            add("artist" to artist)
            year?.takeIf { it > 0 }?.let { add("year" to it.toString()) }
            trackCount?.takeIf { it > 0 }?.let { add("tracks" to it.toString()) }
        }
        val query = parameters.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        return "$SCHEME://$HOST?$query"
    }

    internal companion object {
        const val SCHEME = "wanda"
        const val HOST = "album"

        private const val PREFIX = "$SCHEME://$HOST"

        fun matches(uri: String): Boolean = uri.trim().startsWith("$PREFIX?")

        /**
         * Reads a link, or null when it is not one or carries too little to act on.
         *
         * A title with no artist is refused rather than resolved loosely: "Greatest Hits" resolves
         * to somebody's greatest hits, and playing the wrong record is worse than saying the link
         * was broken.
         */
        fun parse(uri: String): UniversalAlbumLink? {
            if (!matches(uri)) return null
            val parameters = uri.trim()
                .substringAfter("?")
                .split("&")
                .mapNotNull { pair ->
                    val key = pair.substringBefore("=", "")
                    val value = pair.substringAfter("=", "")
                    key.takeIf { it.isNotEmpty() }?.let { it to decode(value) }
                }
                .toMap()

            val title = parameters["title"]?.trim().orEmpty()
            val artist = parameters["artist"]?.trim().orEmpty()
            if (title.isEmpty() || artist.isEmpty()) return null
            return UniversalAlbumLink(
                title = title,
                artist = artist,
                year = parameters["year"]?.toIntOrNull()?.takeIf { it > 0 },
                trackCount = parameters["tracks"]?.toIntOrNull()?.takeIf { it > 0 }
            )
        }

        // Hand-rolled rather than `Uri.Builder`, so that this — the part with the decisions in it —
        // is testable on the JVM. `android.net.Uri` is a stub in unit tests, and a link format
        // that can only be exercised on a device is a link format nobody exercises.
        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")

        private fun decode(value: String): String =
            runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
