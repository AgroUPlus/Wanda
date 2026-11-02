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

    /**
     * The home feed and the discovery pages. A page that fails is skipped; if every page fails the
     * result is that failure, so a caller can tell a broken connection from a feed with no shelves.
     */
    suspend fun getRecommendations(): Result<List<RecommendedShelf>> = coroutineScope {
        val home = async { innerTube.home().map { it.homeShelves() } }
        val discovery = DISCOVERY_BROWSE_IDS.map { browseId ->
            async { innerTube.browse(browseId).map { it.homeShelves() } }
        }

        val pages = (listOf(home) + discovery).map { it.await() }
        val shelves = pages.flatMap { it.getOrDefault(emptyList()) }
        val failure = pages.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (shelves.isEmpty() && failure != null) Result.failure(failure) else Result.success(shelves.distinctBy { it.id })
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

    private val likedShelf = trackShelf(LIKED_BROWSE_ID)

    private val albumShelf = InnerTubeShelfCursor(
        firstPage = { innerTube.browse(LIBRARY_ALBUMS_BROWSE_ID) },
        nextPage = { innerTube.browseContinuation(it) },
        parse = { it.libraryAlbums() }
    )

    suspend fun getLikedTracks(limit: Int, offset: Int): Result<List<UnifiedTrack>> =
        likedShelf.slice(offset, limit)

    /**
     * What the account actually played, newest first, from `FEmusic_history` — one shelf per day.
     * A song played on two days appears on both, so only its latest play is kept. The first page
     * holds a few hundred rows, well beyond what any caller asks for, so no continuation is read.
     */
    suspend fun getRecentTracks(limit: Int): Result<List<UnifiedTrack>> =
        innerTube.browse(HISTORY_BROWSE_ID).map { root ->
            root.responsiveListItems()
                .mapNotNull(::parseResponsiveListItem)
                .distinctBy { it.id }
                .take(limit)
        }

    suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> =
        albumShelf.slice(offset, limit)

    /** A shelf of track rows — liked songs, a playlist — read through its continuations. */
    private fun trackShelf(browseId: String) = InnerTubeShelfCursor(
        firstPage = { innerTube.browse(browseId) },
        nextPage = { innerTube.browseContinuation(it) },
        parse = { page -> page.responsiveListItems().mapNotNull(::parseResponsiveListItem) }
    )

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

    /** Every track, not the first page: a fresh walk each time, since a playlist is edited. */
    suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        trackShelf(playlistId.removePrefix(YTM_PREFIX)).all()

    companion object {
        const val LIKED_BROWSE_ID = "FEmusic_liked_videos"
        const val LIBRARY_ALBUMS_BROWSE_ID = "FEmusic_liked_albums"
        const val HISTORY_BROWSE_ID = "FEmusic_history"
        const val WATCH_URL = "https://music.youtube.com/watch?v="
        const val BROWSE_URL = "https://music.youtube.com/browse/"
        const val PLAYLIST_URL = "https://music.youtube.com/playlist?list="
    }
}
