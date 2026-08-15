package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music via InnerTube. Search and playback work signed out; the personal library and
 * likes need the in-app sign-in (see `YouTubeLoginScreen`).
 */
@Singleton
class YTMusicSource @Inject constructor(
    private val accountManager: GoogleAccountManager,
    private val innerTube: InnerTubeClient,
    private val streamUrlResolver: StreamUrlResolver
) : IMusicSource {

    override val sourceType = SourceType.YTMUSIC
    override val displayName = "YouTube Music"

    override val capabilities = SourceCapabilities(
        search = true,
        albums = true,
        playlists = true,
        likes = true,
        radio = true,
        recommendations = true,
        share = true
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

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> {
        val videoId = trackId.removePrefix(YTM_PREFIX)
        return innerTube.player(videoId).mapCatching { response ->
            // Web variants hand back a scrambled signature rather than a URL, and every variant's
            // URL carries a throttling nonce — both are resolved here.
            val rawUrl = streamUrlResolver.resolve(response.format, videoId)
            // googlevideo separately checks the PO Token that authorized the /player call which
            // minted this URL, when one was used — it has to travel with the fetch too.
            val url = response.streamingPoToken?.let { "$rawUrl&pot=${URLEncoder.encode(it, "UTF-8")}" }
                ?: rawUrl
            // googlevideo also checks the fetch against the client the URL was minted for, so the
            // media request has to keep the same identity as whichever /player call produced it.
            StreamInfo(
                uri = url,
                format = "audio/webm",
                bitRateKbps = 160,
                headers = mapOf("User-Agent" to response.variant.userAgent)
            )
        }
    }

    /**
     * One track by id.
     *
     * Asked of the radio endpoint rather than `/player`: `next` answers with the queue seeded by
     * this video, whose first entry *is* this video, carrying the title, artist, duration and
     * cover that `/player` only exposes as raw `videoDetails`. One request either way, and this
     * one reuses the parsing the radio shelf already goes through.
     */
    override suspend fun getTrack(trackId: String): Result<UnifiedTrack?> {
        val videoId = trackId.removePrefix(YTM_PREFIX)
        return innerTube.next(videoId).map { root ->
            root.playlistPanelVideos()
                .mapNotNull(::parsePlaylistPanelVideo)
                .firstOrNull { it.id == "$YTM_PREFIX$videoId" }
        }
    }

    override suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        innerTube.next(seedTrackId.removePrefix(YTM_PREFIX)).map { root ->
            root.playlistPanelVideos()
                .mapNotNull(::parsePlaylistPanelVideo)
                .filter { it.id != seedTrackId }
                .take(count)
        }

    /**
     * YouTube Music's front page, as YouTube Music itself builds it.
     *
     * The request carries the signed-in cookie ([InnerTubeClient] adds it for `WEB_REMIX`), so the
     * shelves are the account's own recommendations. Signed out this source is not
     * [isConfigured], so the repository never asks — Home falls back to its library-derived
     * shelves rather than showing a stranger's generic feed.
     */
    override suspend fun getRecommendations(): Result<List<RecommendedShelf>> =
        innerTube.home().map { root -> root.homeShelves() }

    /**
     * The canonical watch URL for the video, which is what YouTube Music's own share sheet hands
     * out. No request is made and nothing is minted: unlike a Navidrome share — a token the server
     * has to create — this link already exists and plays for anyone, signed in or not.
     */
    override suspend fun createShareLink(trackId: String, description: String): Result<String> {
        val videoId = trackId.removePrefix(YTM_PREFIX)
        if (videoId.isBlank() || videoId == trackId) {
            return Result.failure(IllegalArgumentException("Not a YouTube Music track: $trackId"))
        }
        return Result.success("$WATCH_URL$videoId")
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

        /** `music.` rather than plain youtube.com, so the link opens in the right app. */
        const val WATCH_URL = "https://music.youtube.com/watch?v="
    }
}
