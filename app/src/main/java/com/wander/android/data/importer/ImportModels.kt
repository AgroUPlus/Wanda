package com.wander.android.data.importer

import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

enum class PlatformType(
    val displayName: String,
    val webUrl: String? = null,
    /** Hosts a link to this platform can be on, subdomains included. */
    private val hosts: List<String> = emptyList()
) {
    SPOTIFY("Spotify", "https://open.spotify.com/", listOf("spotify.com", "spotify.link")),
    DEEZER("Deezer", "https://www.deezer.com/", listOf("deezer.com", "deezer.page.link")),
    YOUTUBE("YouTube Music", "https://music.youtube.com", listOf("youtube.com", "youtu.be")),
    APPLE_MUSIC("Apple Music", "https://music.apple.com", listOf("music.apple.com")),
    PLAIN_TEXT("Text / M3U", null);

    companion object {
        /**
         * Which platform a pasted link is for. Decided by the link's host, not by a substring
         * anywhere in the text, so `evil.example/?u=spotify.com` is not a Spotify link. The first
         * URL in the text is the one that counts, since a share sheet often puts a sentence in
         * front of it.
         */
        fun detect(input: String): PlatformType {
            val trimmed = input.trim()
            if (trimmed.startsWith("spotify:", ignoreCase = true)) return SPOTIFY
            val host = hostOfFirstUrl(trimmed) ?: return PLAIN_TEXT
            return entries.firstOrNull { platform ->
                platform.hosts.any { host == it || host.endsWith(".$it") }
            } ?: PLAIN_TEXT
        }

        private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

        /** The lower-cased host of the first http(s) URL in [text], or null when there is none. */
        internal fun hostOfFirstUrl(text: String): String? {
            val url = URL_PATTERN.find(text)?.value ?: return null
            return try {
                URI(url).host?.lowercase()
            } catch (_: URISyntaxException) {
                null
            }
        }
    }
}

@Serializable
data class RawImportTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L
)

@Serializable
data class RawUserPlaylistSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val platform: PlatformType = PlatformType.SPOTIFY,
    val url: String = ""
)

@Serializable
data class RawImportPlaylist(
    val platform: PlatformType,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val tracks: List<RawImportTrack> = emptyList()
)

sealed interface ImportProgress {
    data object Idle : ImportProgress
    data class Success(
        val playlistId: String,
        val playlistName: String,
        val trackCount: Int
    ) : ImportProgress
    data class Failed(val error: String) : ImportProgress
}
