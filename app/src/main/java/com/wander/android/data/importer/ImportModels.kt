package com.wander.android.data.importer

import kotlinx.serialization.Serializable

enum class PlatformType(val displayName: String, val webUrl: String) {
    SPOTIFY("Spotify", "https://open.spotify.com/"),
    DEEZER("Deezer", "https://www.deezer.com/login"),
    YOUTUBE("YouTube Music", "https://music.youtube.com"),
    APPLE_MUSIC("Apple Music", "https://music.apple.com"),
    PLAIN_TEXT("Text / M3U", "");

    companion object {
        fun detect(input: String): PlatformType = when {
            input.contains("spotify.com", ignoreCase = true) || input.contains("spotify:", ignoreCase = true) -> SPOTIFY
            input.contains("deezer.com", ignoreCase = true) || input.contains("deezer.page.link", ignoreCase = true) -> DEEZER
            input.contains("youtube.com", ignoreCase = true) || input.contains("youtu.be", ignoreCase = true) -> YOUTUBE
            input.contains("apple.com", ignoreCase = true) -> APPLE_MUSIC
            else -> PLAIN_TEXT
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
    data class Fetching(val platform: PlatformType) : ImportProgress
    data class Matching(
        val current: Int,
        val total: Int,
        val currentTrackName: String,
        val matchedCount: Int
    ) : ImportProgress
    data class Saving(val playlistName: String, val trackCount: Int) : ImportProgress
    data class Success(
        val playlistId: String,
        val playlistName: String,
        val matchedCount: Int,
        val totalCount: Int
    ) : ImportProgress
    data class Failed(val error: String) : ImportProgress
}
