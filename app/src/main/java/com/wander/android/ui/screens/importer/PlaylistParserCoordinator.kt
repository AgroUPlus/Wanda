package com.wander.android.ui.screens.importer

import com.wander.android.data.importer.AppleMusicPlaylistParser
import com.wander.android.data.importer.DeezerPlaylistParser
import com.wander.android.data.importer.PlatformType
import com.wander.android.data.importer.RawImportPlaylist
import com.wander.android.data.importer.RawUserPlaylistSummary
import com.wander.android.data.importer.ShortLinkExpander
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
    private val textParser: TextPlaylistParser,
    private val shortLinks: ShortLinkExpander
) {
    suspend fun fetchYouTubePlaylists(): Result<List<RawUserPlaylistSummary>> =
        withContext(Dispatchers.IO) {
            youtubeParser.fetchUserPlaylists()
        }

    suspend fun parsePlaylist(
        url: String,
        fallbackTitle: String? = null,
        fallbackCover: String? = null
    ): Result<RawImportPlaylist> = withContext(Dispatchers.IO) {
        // A phone's share sheet hands out short links that name no playlist; resolve them first.
        val link = shortLinks.expand(url).getOrElse { return@withContext Result.failure(it) }
        val platform = PlatformType.detect(link)

        val result: Result<RawImportPlaylist> = when (platform) {
            PlatformType.SPOTIFY -> spotifyParser.parse(link)
            PlatformType.DEEZER -> deezerParser.parse(link)
            PlatformType.YOUTUBE -> youtubeParser.parse(link)
            PlatformType.APPLE_MUSIC -> appleMusicParser.parse(link)
            PlatformType.PLAIN_TEXT -> textParser.parse(link)
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
