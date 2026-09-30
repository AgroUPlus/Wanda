package com.wander.android.ui.screens.importer

import com.wander.android.data.importer.AppleMusicPlaylistParser
import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawUserPlaylistSummary
import com.wander.android.data.importer.SpotifyPlaylistParser
import com.wander.android.data.importer.TextPlaylistParser
import com.wander.android.data.importer.YouTubePlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates parsing playlists across supported streaming platforms and plain text.
 */
@Singleton
class PlaylistParserCoordinator @Inject constructor(
    private val spotifyParser: SpotifyPlaylistParser,
    private val deezerParser: DeezerPlaylistParser,
    private val youtubeParser: YouTubePlaylistParser,
    private val appleMusicParser: AppleMusicPlaylistParser,
    private val textParser: TextPlaylistParser
) {
    suspend fun fetchYouTubePlaylists(): Result<List<RawUserPlaylistSummary>> =
        withContext(Dispatchers.IO) {
            youtubeParser.fetchUserPlaylists()
        }

    suspend fun fetchDeezerPlaylists(): Result<List<RawUserPlaylistSummary>> =
        withContext(Dispatchers.IO) {
            deezerParser.fetchUserPlaylists()
        }

    suspend fun parsePlaylist(
        url: String,
        fallbackTitle: String? = null,
        fallbackCover: String? = null,
        cookie: String? = null
    ): Result<RawImportPlaylist> = withContext(Dispatchers.IO) {
        val platform = PlatformType.detect(url)

        val result: Result<RawImportPlaylist> = when (platform) {
            PlatformType.SPOTIFY -> spotifyParser.parse(url)
            PlatformType.DEEZER -> deezerParser.parse(url, cookie)
            PlatformType.YOUTUBE -> youtubeParser.parse(url)
            PlatformType.APPLE_MUSIC -> appleMusicParser.parse(url, cookie)
            PlatformType.PLAIN_TEXT -> textParser.parse(url)
        }

        result.map { playlist ->
            val finalTitle = if (!fallbackTitle.isNullOrBlank() && (playlist.title.contains("Playlist", ignoreCase = true) || playlist.title.isBlank())) {
                fallbackTitle
            } else {
                playlist.title
            }
            val finalCover = playlist.coverUrl ?: fallbackCover
            playlist.copy(title = finalTitle, coverUrl = finalCover)
        }
    }
}
