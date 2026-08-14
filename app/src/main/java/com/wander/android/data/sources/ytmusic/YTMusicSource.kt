package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music via InnerTube. Search and playback work signed out; the personal library and
 * likes need the in-app sign-in (see `YouTubeLoginScreen`).
 */
@Singleton
class YTMusicSource @Inject constructor(
    private val accountManager: GoogleAccountManager,
    private val innerTube: InnerTubeClient
) : IMusicSource {

    override val sourceType = SourceType.YTMUSIC
    override val displayName = "YouTube Music"

    override val capabilities = SourceCapabilities(
        search = true,
        albums = true,
        playlists = true,
        likes = true,
        radio = true
    )

    /**
     * Signed-out search still works, but treating the source as configured only when signed in
     * keeps unauthenticated failures out of the library and settings surfaces.
     */
    override val isConfigured: StateFlow<Boolean> = accountManager.isLoggedIn

    override suspend fun search(query: String): Result<List<UnifiedTrack>> =
        innerTube.search(query).map { root ->
            root.responsiveListItems().mapNotNull(::parseResponsiveListItem)
        }

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> =
        innerTube.player(trackId.removePrefix(YTM_PREFIX)).mapCatching { root ->
            val url = root.bestAudioStreamUrl()
                ?: throw IOException("YouTube Music returned no playable audio for this track")
            StreamInfo(
                uri = url,
                format = "audio/webm",
                bitRateKbps = 160,
                // googlevideo checks the fetch against the client the URL was minted for, so the
                // media request has to keep the same identity as the /player call — an anonymous
                // Android Music client. No web Origin: sending one alongside an Android
                // User-Agent describes two different clients, which is what the /player call used
                // to do and why it was refused.
                headers = mapOf(
                    "User-Agent" to InnerTubeVariant.ANDROID_MUSIC.userAgent
                )
            )
        }

    override suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        innerTube.next(seedTrackId.removePrefix(YTM_PREFIX)).map { root ->
            root.playlistPanelVideos()
                .mapNotNull(::parsePlaylistPanelVideo)
                .filter { it.id != seedTrackId }
                .take(count)
        }

    override suspend fun getLikedTracks(limit: Int, offset: Int): Result<List<UnifiedTrack>> =
        innerTube.browse(LIKED_BROWSE_ID).map { root ->
            root.responsiveListItems().mapNotNull(::parseResponsiveListItem).drop(offset).take(limit)
        }

    override suspend fun getRecentTracks(limit: Int) = getLikedTracks(limit, 0)

    override suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> =
        innerTube.browse(LIBRARY_ALBUMS_BROWSE_ID).map { root ->
            root.responsiveListItems()
                .mapNotNull(::parseLibraryAlbum)
                .drop(offset)
                .take(limit)
        }

    override suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        innerTube.browse(albumId.removePrefix(YTM_PREFIX)).map { root ->
            root.responsiveListItems().mapNotNull(::parseResponsiveListItem)
        }

    override suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> = Result.success(
        listOf(
            UnifiedPlaylist(
                id = "$YTM_PREFIX$LIKED_BROWSE_ID",
                source = SourceType.YTMUSIC,
                name = "Liked Music",
                comment = "Tracks you have liked on YouTube Music"
            )
        )
    )

    override suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        innerTube.browse(playlistId.removePrefix(YTM_PREFIX)).map { root ->
            root.responsiveListItems().mapNotNull(::parseResponsiveListItem)
        }

    override suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> {
        if (!accountManager.isLoggedIn.value) {
            return Result.failure(IllegalStateException("Sign in to YouTube Music to like tracks"))
        }
        return innerTube.setLiked(trackId.removePrefix(YTM_PREFIX), liked)
    }

    private companion object {
        const val LIKED_BROWSE_ID = "FEmusic_liked_videos"
        const val LIBRARY_ALBUMS_BROWSE_ID = "FEmusic_liked_albums"
    }
}
