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
import kotlinx.coroutines.flow.MutableStateFlow
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

    /**
     * Always. InnerTube serves search to anonymous callers, and it is the half of this backend
     * that needs no account at all — so a signed-out user still gets YouTube Music results
     * alongside their own library, while Home, likes and Settings stay quiet until they sign in.
     */
    override val isSearchable: StateFlow<Boolean> = MutableStateFlow(true)

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
            val mime = format["mimeType"].text()?.substringBefore(';') ?: "audio/webm"
            val bitrate = format["bitrate"].text()?.toIntOrNull()?.div(1000) ?: 160
            StreamInfo(
                uri = url,
                format = mime,
                bitRateKbps = bitrate,
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

    /**
     * One artist shelf, expanded.
     *
     * The "more" page answers with a grid of the same `musicTwoRowItemRenderer` tiles the carousel
     * held, so it reuses the carousel's own parser — including its filter, which drops any tile
     * that is not an album.
     */
    override suspend fun getArtistAlbumPage(
        browseId: String,
        params: String?,
        artist: String
    ): Result<List<UnifiedAlbum>> =
        innerTube.browse(browseId.removePrefix(YTM_PREFIX), params).map { root ->
            root.artistAlbumGrid(artist, browseId)
        }

    /**
     * An album's tracklist.
     *
     * The rows are stamped with the album they were asked for, and this is not cosmetic: an album
     * page's rows carry a subtitle of `Artist • 3:45` with no album name in it, so the parser had
     * nothing to fill `albumId` from and every track landed in Room with a null one. The album
     * screen reads its tracklist with `WHERE albumId = :albumId`, so the tracks were fetched,
     * persisted, and then never found again — the page showed its header and an empty list.
     *
     * The position is stamped for the same reason. The same query orders by disc and track number,
     * which YouTube does not publish on these rows; left at zero the tracklist came back in
     * whatever order SQLite happened to return, which is not the order of the record.
     */
    override suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        innerTube.browse(albumId.removePrefix(YTM_PREFIX)).map { root ->
            // The page names the artist and shows the sleeve once, at the top; the rows below
            // repeat neither. Without this every track off an album came back credited to
            // "Unknown Artist" and with no cover.
            val header = root.albumHeader()
            root.responsiveListItems()
                .mapNotNull(::parseResponsiveListItem)
                .mapIndexed { index, track ->
                    track.copy(
                        albumId = albumId,
                        trackNumber = index + 1,
                        // Only where the row could not speak for itself. A featured artist on one
                        // track of a compilation is named on that row, and the header's credit
                        // must not overwrite them.
                        artist = track.artist.takeUnless { it == UNKNOWN_ARTIST }
                            ?: header?.artist
                            ?: track.artist,
                        artistId = track.artistId ?: header?.artistId,
                        // Album rows carry no thumbnail of their own — the sleeve is printed once
                        // at the top of the page. Without this the same song had a cover when
                        // found by search and none when opened from its own record.
                        artworkUrl = track.artworkUrl ?: header?.coverArtUrl
                    )
                }
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
