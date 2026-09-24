package com.wander.android.data.sources.navidrome

import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

internal const val NAVIDROME_PREFIX = "navidrome:"

/**
 * Handles catalog fetching, browsing, searching, and Subsonic model conversion for Navidrome.
 */
@Singleton
class NavidromeCatalogLoader @Inject constructor(
    private val apiClient: SubsonicApiClient
) {
    suspend fun search(query: String): Result<List<UnifiedTrack>> =
        apiClient.search3(query).map { result -> result.song.orEmpty().map { it.toUnified() } }

    suspend fun getTrack(trackId: String): Result<UnifiedTrack?> =
        apiClient.getSong(trackId.removePrefix(NAVIDROME_PREFIX)).map { it?.toUnified() }

    suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        apiClient.getSimilarSongs2(seedTrackId.removePrefix(NAVIDROME_PREFIX), count)
            .map { songs -> songs.map { it.toUnified() } }

    suspend fun getLikedTracks(limit: Int, offset: Int): Result<List<UnifiedTrack>> =
        apiClient.getStarred2().map { starred -> starred.song.orEmpty().map { it.toUnified() } }

    suspend fun getRecentTracks(limit: Int): Result<List<UnifiedTrack>> = coroutineScope {
        apiClient.getAlbumList2(type = "newest", size = RECENT_ALBUMS).mapCatching { albums ->
            albums
                .map { album -> async { apiClient.getAlbum(album.id).getOrNull()?.song.orEmpty() } }
                .flatMap { it.await() }
                .map { it.toUnified() }
                .take(limit)
        }
    }

    suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> =
        apiClient.getAlbumList2(type = "alphabeticalByName", size = limit, offset = offset)
            .map { albums -> albums.map { it.toUnified() } }

    suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        apiClient.getAlbum(albumId.removePrefix(NAVIDROME_PREFIX))
            .map { album -> album.song.orEmpty().map { it.toUnified() } }

    suspend fun getArtist(artistId: String): Result<ArtistDetails> {
        val id = artistId.removePrefix(NAVIDROME_PREFIX)
        return apiClient.getArtist(id).map { artist ->
            val info = apiClient.getArtistInfo2(id).getOrNull()
            val albums = artist.album.orEmpty().map { it.toUnified() }
            ArtistDetails(
                id = "$NAVIDROME_PREFIX${artist.id}",
                name = artist.name,
                imageUrl = null,
                bio = info?.biography?.stripBiographyMarkup(),
                sections = if (albums.isEmpty()) {
                    emptyList()
                } else {
                    listOf(ArtistAlbumSection("Albums", albums))
                }
            )
        }
    }

    suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> = apiClient.getPlaylists().map { list ->
        list.map { playlist ->
            UnifiedPlaylist(
                id = "$NAVIDROME_PREFIX${playlist.id}",
                source = SourceType.NAVIDROME,
                name = playlist.name,
                comment = playlist.comment,
                coverArtUrl = apiClient.buildCoverArtUrl(playlist.coverArt),
                songCount = playlist.songCount,
                durationMs = playlist.duration * 1000L,
                isPublic = playlist.public
            )
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        apiClient.getPlaylist(playlistId.removePrefix(NAVIDROME_PREFIX))
            .map { detail -> detail.entry.orEmpty().map { it.toUnified() } }

    fun SubsonicSong.toUnified() = UnifiedTrack(
        id = "$NAVIDROME_PREFIX$id",
        source = SourceType.NAVIDROME,
        title = title,
        artist = artist ?: "Unknown Artist",
        album = album,
        albumId = albumId?.let { "$NAVIDROME_PREFIX$it" },
        artistId = artistId?.let { "$NAVIDROME_PREFIX$it" },
        durationMs = (duration ?: 0L) * 1000L,
        artworkUrl = apiClient.buildCoverArtUrl(coverArt),
        trackNumber = track,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRateKbps = bitRate,
        format = suffix ?: contentType,
        isLiked = starred != null,
        playCount = playCount
    )

    fun SubsonicAlbum.toUnified() = UnifiedAlbum(
        id = "$NAVIDROME_PREFIX$id",
        source = SourceType.NAVIDROME,
        title = name,
        artist = artist ?: "Unknown Artist",
        artistId = artistId?.let { "$NAVIDROME_PREFIX$it" },
        coverArtUrl = apiClient.buildCoverArtUrl(coverArt),
        songCount = songCount,
        durationMs = duration * 1000L,
        year = year,
        genre = genre
    )

    private companion object {
        const val RECENT_ALBUMS = 25
    }
}
