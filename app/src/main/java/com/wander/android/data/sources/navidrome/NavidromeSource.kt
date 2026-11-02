package com.wander.android.data.sources.navidrome

import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.security.SecureStorage
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
import com.wander.android.data.sources.ShareKind
import com.wander.android.data.sources.ShareTarget
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

private const val PREFIX = "navidrome:"

/** The most capable backend: a Subsonic-compatible server, usually self-hosted Navidrome. */
@Singleton
class NavidromeSource @Inject constructor(
    private val secureStorage: SecureStorage,
    private val apiClient: SubsonicApiClient,
    private val catalogLoader: NavidromeCatalogLoader,
    /** Which of the two quality settings applies: the metered one or the Wi-Fi one. */
    private val connectivity: ConnectivityObserver,
    private val homeFeed: NavidromeHomeFeed
) : IMusicSource {

    override val sourceType = SourceType.NAVIDROME
    override val displayName = "Navidrome"

    override val capabilities = SourceCapabilities(
        search = true,
        albums = true,
        artists = true,
        playlists = true,
        playlistWrite = true,
        likes = true,
        scrobble = true,
        radio = true,
        lyrics = true,
        recommendations = true,
        share = true,
        collectionSearch = true
    )

    override val isConfigured: StateFlow<Boolean> = secureStorage.navidromeConfigured

    private val lyrics = NavidromeLyrics(apiClient)

    init {
        applyStoredCredentials()
    }

    private fun applyStoredCredentials() {
        apiClient.configure(
            secureStorage.navidromeServerUrl,
            secureStorage.navidromeUsername,
            secureStorage.navidromePassword
        )
    }

    /**
     * Validates credentials against the server before storing them, so a typo surfaces on the
     * login screen rather than as an empty library later.
     */
    suspend fun login(url: String, username: String, password: String): Result<Unit> {
        apiClient.configure(url, username, password)
        return apiClient.ping()
            .onSuccess { secureStorage.setNavidromeCredentials(url, username, password) }
            .onFailure { applyStoredCredentials() }
            .map { }
    }

    fun logout() {
        secureStorage.clearNavidromeCredentials()
        apiClient.configure("", "", "")
    }

    // ── Reads ───────────────────────────────────────────────────────────────────────────────

    override suspend fun search(query: String): Result<List<UnifiedTrack>> =
        catalogLoader.search(query)

    /** One `search3` call answers songs, albums and artists together. Music only, like [search]. */
    override suspend fun searchCatalog(query: String, kind: SearchKind): Result<SearchCatalog> =
        if (kind == SearchKind.TRACKS) catalogLoader.searchCatalog(query) else Result.success(SearchCatalog())

    override suspend fun getTrack(trackId: String): Result<UnifiedTrack?> =
        catalogLoader.getTrack(trackId)

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> {
        if (!apiClient.isConfigured) return Result.failure(IllegalStateException("Navidrome not configured"))
        if (trackId.startsWith(NavidromeHomeFeed.RADIO_PREFIX)) return homeFeed.radioStream(trackId)
        val quality = if (connectivity.isMetered) {
            secureStorage.playbackPrefs.navidromeQualityMobile.value
        } else {
            secureStorage.playbackPrefs.navidromeQualityWifi.value
        }
        return Result.success(
            StreamInfo(
                uri = apiClient.buildStreamUrl(trackId.removePrefix(PREFIX), quality),
                format = quality.mimeType,
                bitRateKbps = quality.maxBitRateKbps.takeIf { it > 0 } ?: 320
            )
        )
    }

    /** See [NavidromeLyrics] for which call a server is asked. */
    override suspend fun getLyrics(trackId: String): Result<LyricsData?> =
        lyrics.fetch(trackId, trackId.removePrefix(PREFIX))

    /** See [NavidromeHomeFeed] for the shelves. */
    override suspend fun getRecommendations(): Result<List<RecommendedShelf>> = homeFeed.shelves()

    override suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        catalogLoader.getRadio(seedTrackId, count)

    override suspend fun getLikedTracks(limit: Int, offset: Int): Result<List<UnifiedTrack>> =
        catalogLoader.getLikedTracks(limit, offset)

    override suspend fun getRecentTracks(limit: Int): Result<List<UnifiedTrack>> =
        catalogLoader.getRecentTracks(limit)

    override suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> =
        catalogLoader.getAlbums(limit, offset)

    override suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        catalogLoader.getAlbumTracks(albumId)

    override suspend fun getArtist(artistId: String): Result<ArtistDetails> =
        catalogLoader.getArtist(artistId)

    override suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> =
        catalogLoader.getPlaylists()

    override suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        catalogLoader.getPlaylistTracks(playlistId)

    // ── Writes ──────────────────────────────────────────────────────────────────────────────

    suspend fun startScan(): Result<Unit> = apiClient.startScan()

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> {
        val songIds = trackIds.map { it.removePrefix(PREFIX) }
        val created = apiClient.createPlaylist(name, songIds)
        created.exceptionOrNull()?.let { return Result.failure(it) }

        created.getOrNull()?.let { return Result.success("$PREFIX$it") }

        return apiClient.getPlaylists().mapCatching { playlists ->
            val match = playlists.lastOrNull { it.name == name }
                ?: throw IOException("The server accepted the playlist but did not return it")
            "$PREFIX${match.id}"
        }
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> =
        apiClient.updatePlaylist(
            playlistId = playlistId.removePrefix(PREFIX),
            songIdsToAdd = trackIds.map { it.removePrefix(PREFIX) }
        )

    override suspend fun updatePlaylistDetails(playlistId: String, name: String, comment: String?): Result<Unit> =
        apiClient.updatePlaylistDetails(playlistId.removePrefix(PREFIX), name, comment.orEmpty())

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        apiClient.deletePlaylist(playlistId.removePrefix(PREFIX))

    override suspend fun removeFromPlaylist(playlistId: String, index: Int): Result<Unit> =
        apiClient.removeFromPlaylist(playlistId.removePrefix(PREFIX), index)

    /**
     * Reads the entries afresh and writes them back in the new order. The index is the screen's,
     * which was read from this same list; one that no longer fits is refused, not guessed at.
     */
    override suspend fun moveInPlaylist(playlistId: String, from: Int, to: Int): Result<Unit> {
        val id = playlistId.removePrefix(PREFIX)
        val songIds = apiClient.getPlaylist(id).getOrElse { return Result.failure(it) }
            .entry.orEmpty().map { it.id }.toMutableList()
        if (from !in songIds.indices || to !in songIds.indices) {
            return Result.failure(IndexOutOfBoundsException("The playlist changed in the meantime"))
        }
        songIds.add(to, songIds.removeAt(from))
        return apiClient.replacePlaylistSongs(id, songIds)
    }

    /**
     * A song already shared on its own gets the share it has; anything else gets a new one. When
     * the server cannot list its shares, the share is made anyway — the user asked for a link, and
     * a duplicate is a smaller failure than none.
     */
    override suspend fun createShareLink(target: ShareTarget): Result<String> {
        val id = target.id.removePrefix(PREFIX)
        if (target.kind == ShareKind.TRACK) {
            apiClient.getShares().getOrNull()
                ?.let { reusableShare(it, id, Instant.now()) }
                ?.let { return Result.success(it.url) }
        }
        return apiClient.createShare(listOf(id), target.description)
    }

    override suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> {
        val id = trackId.removePrefix(PREFIX)
        return if (liked) apiClient.star(id) else apiClient.unstar(id)
    }

    override suspend fun scrobble(trackId: String, submissionTime: Long): Result<Unit> {
        if (secureStorage.isIncognitoMode) return Result.success(Unit)
        return apiClient.scrobble(trackId.removePrefix(PREFIX), submissionTime / 1000L)
    }

    override suspend fun nowPlaying(trackId: String): Result<Unit> {
        if (secureStorage.isIncognitoMode) return Result.success(Unit)
        return apiClient.nowPlaying(trackId.removePrefix(PREFIX))
    }

    suspend fun resolveShare(shareUrlOrId: String): Result<UnifiedTrack> =
        apiClient.getShares().mapCatching { shares ->
            val share = shares.firstOrNull {
                it.id == shareUrlOrId || it.url == shareUrlOrId || it.url.endsWith("/$shareUrlOrId") ||
                    (it.id.isNotBlank() && shareUrlOrId.contains(it.id))
            } ?: throw IOException("Share not found or expired on Navidrome server")
            with(catalogLoader) {
                share.entry?.firstOrNull()?.toUnified()
                    ?: throw IOException("Navidrome share has no playable songs")
            }
        }
}
