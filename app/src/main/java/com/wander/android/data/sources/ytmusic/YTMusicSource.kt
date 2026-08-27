package com.wander.android.data.sources.ytmusic

import androidx.media3.common.MimeTypes
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
        artists = true,
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
        search(query, SearchKind.TRACKS)

    /**
     * Episodes and videos parse with the same reader as songs — they arrive as the same
     * `musicResponsiveListItemRenderer`, and `parseResponsiveListItem` drops anything without a
     * `videoId`, so a podcast *show* (which has none) is skipped rather than added as something
     * unplayable.
     */
    override suspend fun search(query: String, kind: SearchKind): Result<List<UnifiedTrack>> =
        innerTube.search(query, kind).map { root ->
            root.responsiveListItems().mapNotNull(::parseResponsiveListItem)
        }

    /**
     * Who is signed in, for Settings to name.
     *
     * Served from the cache when there is one, so opening Settings is not a network call. A
     * failure leaves the cache alone and answers with whatever was already known — "signed in"
     * is still true when the name simply could not be fetched.
     */
    suspend fun accountName(): String {
        if (!accountManager.isLoggedIn.value) return ""
        accountManager.accountName.takeIf { it.isNotBlank() }?.let { return it }
        val fetched = innerTube.accountName().getOrNull()?.takeIf { it.isNotBlank() } ?: return ""
        accountManager.rememberAccountName(fetched)
        return fetched
    }

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> {
        val videoId = trackId.removePrefix(YTM_PREFIX)
        return innerTube.player(videoId).mapCatching { response ->
            // A livestream is the manifest and nothing else: there is no signature to unscramble
            // and no throttling nonce, and appending a PO Token to a manifest URL invalidates it.
            response.hlsManifestUrl?.let { manifest ->
                return@mapCatching StreamInfo(
                    uri = manifest,
                    format = MimeTypes.APPLICATION_M3U8,
                    bitRateKbps = 0,
                    headers = mapOf("User-Agent" to response.variant.userAgent)
                )
            }
            val format = response.format
                ?: throw IOException("YouTube Music returned no playable audio for this track")
            // Web variants hand back a scrambled signature rather than a URL, and every variant's
            // URL carries a throttling nonce — both are resolved here.
            val rawUrl = streamUrlResolver.resolve(format, videoId)
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
    /**
     * The personalised front page, plus the standing discovery feeds behind it.
     *
     * The home feed alone leans hard on what you already play, which is exactly the complaint it
     * earns — it goes stale and repeats. New releases, charts and explore are not personalised at
     * all, and that is the point: they are the part of the feed that can still surprise you.
     *
     * The extra feeds are fetched in parallel and each one is allowed to fail on its own. A
     * discovery feed that 404s one day must not take the personalised feed down with it.
     */
    override suspend fun getRecommendations(): Result<List<RecommendedShelf>> = coroutineScope {
        val home = async { innerTube.home().map { it.homeShelves() }.getOrDefault(emptyList()) }
        val discovery = DISCOVERY_BROWSE_IDS.map { browseId ->
            async { innerTube.browse(browseId).map { it.homeShelves() }.getOrDefault(emptyList()) }
        }

        val shelves = (listOf(home) + discovery).flatMap { it.await() }
        // Distinct by id: the discovery feeds overlap each other — "New albums & singles" turns up
        // under both new releases and explore — and a duplicate id would collide in the cache.
        Result.success(shelves.distinctBy { it.id })
    }

    /**
     * The canonical watch URL for the video, which is what YouTube Music's own share sheet hands
     * out. No request is made and nothing is minted: unlike a Navidrome share — a token the server
     * has to create — this link already exists and plays for anyone, signed in or not.
     */
    override suspend fun createShareLink(target: ShareTarget): Result<String> {
        val id = target.id.removePrefix(YTM_PREFIX)
        if (id.isBlank() || id == target.id) {
            return Result.failure(IllegalArgumentException("Not a YouTube Music id: ${target.id}"))
        }
        // A playlist id is the one YouTube addresses by query parameter rather than by path; an
        // album and an artist are both browse pages, which is why they share a form.
        return Result.success(
            when (target.kind) {
                ShareKind.TRACK -> "$WATCH_URL$id"
                ShareKind.PLAYLIST -> "$PLAYLIST_URL$id"
                ShareKind.ALBUM, ShareKind.ARTIST -> "$BROWSE_URL$id"
            }
        )
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

    /**
     * Their page as YouTube Music serves it — bio, portrait, and the shelves in YouTube's order.
     *
     * The id is the artist's channel browse id, which every track parsed from this source now
     * carries; see `InnerTubeSubtitle.artistId`. A response that carries no header is not an
     * artist page, and saying so is better than returning a page with a name and nothing else.
     */
    override suspend fun getArtist(artistId: String): Result<ArtistDetails> {
        val browseId = artistId.removePrefix(YTM_PREFIX)
        if (browseId.isBlank()) {
            return Result.failure(IllegalArgumentException("No YouTube Music artist id"))
        }
        return innerTube.browse(browseId).mapCatching { body ->
            body.artistPage(browseId)
                ?: throw IOException("YouTube Music returned no artist page for this id")
        }
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
        const val BROWSE_URL = "https://music.youtube.com/browse/"
        const val PLAYLIST_URL = "https://music.youtube.com/playlist?list="
    }
}
