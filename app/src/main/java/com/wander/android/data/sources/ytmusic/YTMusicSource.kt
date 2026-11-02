package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SearchCatalog
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.ShareTarget
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * YouTube Music via InnerTube. Search and playback work signed out; the personal library and
 * likes need the in-app sign-in (see `YouTubeLoginScreen`).
 */
@Singleton
class YTMusicSource @Inject constructor(
    private val accountManager: GoogleAccountManager,
    private val innerTube: InnerTubeClient,
    private val streamResolver: YTMusicStreamResolver,
    private val catalogLoader: YTMusicCatalogLoader,
    private val playlistWriter: YTMusicPlaylistWriter
) : IMusicSource {

    constructor(
        accountManager: GoogleAccountManager,
        innerTube: InnerTubeClient,
        streamUrlResolver: StreamUrlResolver
    ) : this(
        accountManager = accountManager,
        innerTube = innerTube,
        streamResolver = YTMusicStreamResolver(innerTube, streamUrlResolver),
        catalogLoader = YTMusicCatalogLoader(innerTube),
        playlistWriter = YTMusicPlaylistWriter(innerTube)
    )

    override val sourceType = SourceType.YTMUSIC
    override val displayName = "YouTube Music"

    override val capabilities = SourceCapabilities(
        search = true,
        albums = true,
        artists = true,
        playlists = true,
        playlistWrite = true,
        likes = true,
        radio = true,
        lyrics = true,
        recommendations = true,
        share = true,
        videoClips = true,
        collectionSearch = true
    )

    /**
     * Signed-out search still works, but treating the source as configured only when signed in
     * keeps unauthenticated failures out of the library and settings surfaces.
     */
    override val isConfigured: StateFlow<Boolean> = accountManager.isLoggedIn

    /**
     * Always. InnerTube serves search to anonymous callers, and it is the half of this backend
     * that needs no account at all — so a signed-out user still gets YouTube Music results
     * alongside their own library, while Home, likes and Settings stay quiet until they sign in.
     */
    override val isSearchable: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun search(query: String): Result<List<UnifiedTrack>> =
        catalogLoader.search(query, SearchKind.TRACKS)

    override suspend fun search(query: String, kind: SearchKind): Result<List<UnifiedTrack>> =
        catalogLoader.search(query, kind)

    override suspend fun searchCatalog(query: String, kind: SearchKind): Result<SearchCatalog> =
        catalogLoader.searchCatalog(query, kind)

    /**
     * Who is signed in, for Settings to name.
     */
    suspend fun accountName(): String {
        if (!accountManager.isLoggedIn.value) return ""
        accountManager.accountName.takeIf { it.isNotBlank() }?.let { return it }
        val fetched = innerTube.accountName().getOrNull()?.takeIf { it.isNotBlank() } ?: return ""
        accountManager.rememberAccountName(fetched)
        return fetched
    }

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> =
        streamResolver.getStreamInfo(trackId)

    override suspend fun getVideoStreamInfo(trackId: String): Result<StreamInfo> =
        streamResolver.getVideoStreamInfo(trackId)

    override suspend fun getTrack(trackId: String): Result<UnifiedTrack?> =
        catalogLoader.getTrack(trackId)

    override suspend fun getLyrics(trackId: String): Result<LyricsData?> =
        catalogLoader.getLyrics(trackId)

    override suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        catalogLoader.getRadio(seedTrackId, count)

    override suspend fun getRecommendations(): Result<List<RecommendedShelf>> =
        catalogLoader.getRecommendations()

    override suspend fun createShareLink(target: ShareTarget): Result<String> =
        catalogLoader.createShareLink(target)

    override suspend fun getLikedTracks(limit: Int, offset: Int): Result<List<UnifiedTrack>> =
        catalogLoader.getLikedTracks(limit, offset)

    override suspend fun getRecentTracks(limit: Int): Result<List<UnifiedTrack>> =
        catalogLoader.getRecentTracks(limit)

    override suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> =
        catalogLoader.getAlbums(limit, offset)

    override suspend fun getArtist(artistId: String): Result<ArtistDetails> =
        catalogLoader.getArtist(artistId)

    override suspend fun getArtistAlbumPage(
        browseId: String,
        params: String?,
        artist: String
    ): Result<List<UnifiedAlbum>> =
        catalogLoader.getArtistAlbumPage(browseId, params, artist)

    override suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        catalogLoader.getAlbumTracks(albumId)

    override suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> =
        catalogLoader.getPlaylists()

    override suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        catalogLoader.getPlaylistTracks(playlistId)

    override suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> {
        if (!accountManager.isLoggedIn.value) {
            return Result.failure(IllegalStateException("Sign in to YouTube Music to like tracks"))
        }
        return innerTube.setLiked(trackId.removePrefix(YTM_PREFIX), liked)
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> =
        signedIn { playlistWriter.create(name, trackIds) }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> =
        signedIn { playlistWriter.add(playlistId, trackIds) }

    override suspend fun removeFromPlaylist(playlistId: String, index: Int): Result<Unit> =
        signedIn { playlistWriter.remove(playlistId, index) }

    override suspend fun moveInPlaylist(playlistId: String, from: Int, to: Int): Result<Unit> =
        signedIn { playlistWriter.move(playlistId, from, to) }

    override suspend fun updatePlaylistDetails(
        playlistId: String,
        name: String,
        comment: String?
    ): Result<Unit> = signedIn { playlistWriter.updateDetails(playlistId, name, comment) }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        signedIn { playlistWriter.delete(playlistId) }

    /** Playlist writes go to the account's own library, so they need one signed in. */
    private inline fun <T> signedIn(write: () -> Result<T>): Result<T> =
        if (accountManager.isLoggedIn.value) {
            write()
        } else {
            Result.failure(IllegalStateException("Sign in to YouTube Music to change playlists"))
        }
}
