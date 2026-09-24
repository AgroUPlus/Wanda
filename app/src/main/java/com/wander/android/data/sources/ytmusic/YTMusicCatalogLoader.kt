package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UNKNOWN_ARTIST
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles catalog browsing, search queries, recommendations, artist details, and album tracklists
 * via InnerTube for YouTube Music.
 */
@Singleton
class YTMusicCatalogLoader @Inject constructor(
    private val innerTube: InnerTubeClient
) {
    suspend fun search(query: String, kind: SearchKind): Result<List<UnifiedTrack>> =
        innerTube.search(query, kind).map { root ->
            val episodes = kind == SearchKind.EPISODES
            root.responsiveListItems()
                .mapNotNull(::parseResponsiveListItem)
                .map { if (episodes) it.copy(isEpisode = true) else it }
        }

    suspend fun getTrack(trackId: String): Result<UnifiedTrack?> {
        val videoId = trackId.removePrefix(YTM_PREFIX)
        return innerTube.next(videoId).map { root ->
            root.playlistPanelVideos()
                .mapNotNull(::parsePlaylistPanelVideo)
                .firstOrNull { it.id == "$YTM_PREFIX$videoId" }
        }
    }

    suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        innerTube.next(seedTrackId.removePrefix(YTM_PREFIX)).map { root ->
            root.playlistPanelVideos()
                .mapNotNull(::parsePlaylistPanelVideo)
                .filter { it.id != seedTrackId }
                .take(count)
        }

    suspend fun getRecommendations(): Result<List<RecommendedShelf>> = coroutineScope {
        val home = async { innerTube.home().map { it.homeShelves() }.getOrDefault(emptyList()) }
        val discovery = DISCOVERY_BROWSE_IDS.map { browseId ->
            async { innerTube.browse(browseId).map { it.homeShelves() }.getOrDefault(emptyList()) }
        }

        val shelves = (listOf(home) + discovery).flatMap { it.await() }
        Result.success(shelves.distinctBy { it.id })
    }

    suspend fun createShareLink(target: ShareTarget): Result<String> {
        val id = target.id.removePrefix(YTM_PREFIX)
        if (id.isBlank() || id == target.id) {
            return Result.failure(IllegalArgumentException("Not a YouTube Music id: ${target.id}"))
        }
        return Result.success(
            when (target.kind) {
                ShareKind.TRACK -> "$WATCH_URL$id"
                ShareKind.PLAYLIST -> "$PLAYLIST_URL$id"
                ShareKind.ALBUM, ShareKind.ARTIST -> "$BROWSE_URL$id"
            }
        )
    }

    suspend fun getLikedTracks(limit: Int, offset: Int): Result<List<UnifiedTrack>> =
        innerTube.browse(LIKED_BROWSE_ID).map { root ->
            root.responsiveListItems().mapNotNull(::parseResponsiveListItem).drop(offset).take(limit)
        }

    suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> =
        innerTube.browse(LIBRARY_ALBUMS_BROWSE_ID).map { root ->
            root.responsiveListItems()
                .mapNotNull(::parseLibraryAlbum)
                .drop(offset)
                .take(limit)
        }

    suspend fun getArtist(artistId: String): Result<ArtistDetails> {
        val browseId = artistId.removePrefix(YTM_PREFIX)
        if (browseId.isBlank()) {
            return Result.failure(IllegalArgumentException("No YouTube Music artist id"))
        }
        return innerTube.browse(browseId).mapCatching { body ->
            body.artistPage(browseId)
                ?: throw IOException("YouTube Music returned no artist page for this id")
        }
    }

    suspend fun getArtistAlbumPage(
        browseId: String,
        params: String?,
        artist: String
    ): Result<List<UnifiedAlbum>> =
        innerTube.browse(browseId.removePrefix(YTM_PREFIX), params).map { root ->
            root.artistAlbumGrid(artist, browseId)
        }

    suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        innerTube.browse(albumId.removePrefix(YTM_PREFIX)).map { root ->
            val header = root.albumHeader()
            root.responsiveListItems()
                .mapNotNull(::parseResponsiveListItem)
                .mapIndexed { index, track ->
                    track.copy(
                        albumId = albumId,
                        trackNumber = index + 1,
                        artist = track.artist.takeUnless { it == UNKNOWN_ARTIST }
                            ?: header?.artist
                            ?: track.artist,
                        artistId = track.artistId ?: header?.artistId,
                        artworkUrl = track.artworkUrl ?: header?.coverArtUrl
                    )
                }
        }

    fun getPlaylists(): Result<List<UnifiedPlaylist>> = Result.success(
        listOf(
            UnifiedPlaylist(
                id = "$YTM_PREFIX$LIKED_BROWSE_ID",
                source = SourceType.YTMUSIC,
                name = "Liked Music",
                comment = "Tracks you have liked on YouTube Music"
            )
        )
    )

    suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        innerTube.browse(playlistId.removePrefix(YTM_PREFIX)).map { root ->
            root.responsiveListItems().mapNotNull(::parseResponsiveListItem)
        }

    companion object {
        const val LIKED_BROWSE_ID = "FEmusic_liked_videos"
        const val LIBRARY_ALBUMS_BROWSE_ID = "FEmusic_liked_albums"
        const val WATCH_URL = "https://music.youtube.com/watch?v="
        const val BROWSE_URL = "https://music.youtube.com/browse/"
        const val PLAYLIST_URL = "https://music.youtube.com/playlist?list="
    }
}
