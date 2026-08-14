package com.wander.android.data.sources.navidrome

import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.LyricLine
import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFIX = "navidrome:"

/** The most capable backend: a Subsonic-compatible server, usually self-hosted Navidrome. */
@Singleton
class NavidromeSource @Inject constructor(
    private val secureStorage: SecureStorage,
    private val apiClient: SubsonicApiClient
) : IMusicSource {

    override val sourceType = SourceType.NAVIDROME
    override val displayName = "Navidrome"

    override val capabilities = SourceCapabilities(
        search = true,
        albums = true,
        playlists = true,
        likes = true,
        scrobble = true,
        radio = true,
        lyrics = true
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

    override suspend fun search(query: String) =
        apiClient.search3(query).map { result -> result.song.orEmpty().map { it.toUnified() } }

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

    override suspend fun getRadio(seedTrackId: String, count: Int) =
        apiClient.getSimilarSongs2(seedTrackId.removePrefix(PREFIX), count)
            .map { songs -> songs.map { it.toUnified() } }

    override suspend fun getLikedTracks(limit: Int, offset: Int) =
        apiClient.getStarred2().map { starred -> starred.song.orEmpty().map { it.toUnified() } }

    override suspend fun getRecentTracks(limit: Int) =
        apiClient.getAlbumList2(type = "recent", size = RECENT_ALBUMS).mapCatching { albums ->
            albums.flatMap { album ->
                apiClient.getAlbum(album.id).getOrNull()?.song.orEmpty().map { it.toUnified() }
            }.take(limit)
        }

    override suspend fun getAlbums(limit: Int, offset: Int) =
        apiClient.getAlbumList2(type = "alphabeticalByName", size = limit)
            .map { albums -> albums.map { it.toUnified() } }

    override suspend fun getAlbumTracks(albumId: String) =
        apiClient.getAlbum(albumId.removePrefix(PREFIX))
            .map { album -> album.song.orEmpty().map { it.toUnified() } }

    override suspend fun getPlaylists() = apiClient.getPlaylists().map { list ->
        list.map { playlist ->
            UnifiedPlaylist(
                id = "$PREFIX${playlist.id}",
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

    override suspend fun getPlaylistTracks(playlistId: String) =
        apiClient.getPlaylist(playlistId.removePrefix(PREFIX))
            .map { detail -> detail.entry.orEmpty().map { it.toUnified() } }

    // ── Writes ──────────────────────────────────────────────────────────────────────────────

    override suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> {
        val id = trackId.removePrefix(PREFIX)
        return if (liked) apiClient.star(id) else apiClient.unstar(id)
    }

    override suspend fun scrobble(trackId: String, submissionTime: Long): Result<Unit> {
        if (secureStorage.isIncognitoMode) return Result.success(Unit)
        return apiClient.scrobble(trackId.removePrefix(PREFIX), submissionTime / 1000L)
    }

    private fun SubsonicSong.toUnified() = UnifiedTrack(
        id = "$PREFIX$id",
        source = SourceType.NAVIDROME,
        title = title,
        artist = artist ?: "Unknown Artist",
        album = album,
        albumId = albumId?.let { "$PREFIX$it" },
        artistId = artistId?.let { "$PREFIX$it" },
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

    private fun SubsonicAlbum.toUnified() = UnifiedAlbum(
        id = "$PREFIX$id",
        source = SourceType.NAVIDROME,
        title = name,
        artist = artist ?: "Unknown Artist",
        artistId = artistId?.let { "$PREFIX$it" },
        coverArtUrl = apiClient.buildCoverArtUrl(coverArt),
        songCount = songCount,
        durationMs = duration * 1000L,
        year = year,
        genre = genre
    )

    private companion object {
        const val RECENT_ALBUMS = 8
    }
}
