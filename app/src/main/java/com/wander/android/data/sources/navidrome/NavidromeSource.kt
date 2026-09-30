package com.wander.android.data.sources.navidrome

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.ShareTarget
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

private const val PREFIX = "navidrome:"

/** The most capable backend: a Subsonic-compatible server, usually self-hosted Navidrome. */
@Singleton
class NavidromeSource @Inject constructor(
    private val secureStorage: SecureStorage,
    private val apiClient: SubsonicApiClient,
    private val catalogLoader: NavidromeCatalogLoader
) : IMusicSource {

    constructor(
        secureStorage: SecureStorage,
        apiClient: SubsonicApiClient
    ) : this(
        secureStorage = secureStorage,
        apiClient = apiClient,
        catalogLoader = NavidromeCatalogLoader(apiClient)
    )

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
        share = true
    )

    override val isConfigured: StateFlow<Boolean> = secureStorage.navidromeConfigured

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

    override suspend fun getTrack(trackId: String): Result<UnifiedTrack?> =
        catalogLoader.getTrack(trackId)

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> {
        if (!apiClient.isConfigured) return Result.failure(IllegalStateException("Navidrome not configured"))
        return Result.success(
            StreamInfo(
                uri = apiClient.buildStreamUrl(trackId.removePrefix(PREFIX)),
                format = "audio/*",
                bitRateKbps = 320
            )
        )
    }

    override suspend fun getLyrics(trackId: String): Result<LyricsData?> =
        apiClient.getLyricsBySongId(trackId.removePrefix(PREFIX)).map { list ->
            val structured = list?.structuredLyrics?.firstOrNull() ?: return@map null
            val lines = structured.line.orEmpty()
            if (lines.isEmpty()) return@map null
            LyricsData(
                trackId = trackId,
                isSynced = structured.synced,
                lines = lines.map { LyricLine(timestampMs = it.start ?: 0L, text = it.value) },
                source = "Navidrome"
            )
        }

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

    override suspend fun createShareLink(target: ShareTarget): Result<String> =
        apiClient.createShare(listOf(target.id.removePrefix(PREFIX)), target.description)

    override suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> {
        val id = trackId.removePrefix(PREFIX)
        return if (liked) apiClient.star(id) else apiClient.unstar(id)
    }

    override suspend fun scrobble(trackId: String, submissionTime: Long): Result<Unit> {
        if (secureStorage.isIncognitoMode) return Result.success(Unit)
        return apiClient.scrobble(trackId.removePrefix(PREFIX), submissionTime / 1000L)
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
