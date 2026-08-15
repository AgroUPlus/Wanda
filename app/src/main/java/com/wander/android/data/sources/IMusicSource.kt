package com.wander.android.data.sources

import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.flow.StateFlow

data class StreamInfo(
    val uri: String,
    val format: String = "audio/opus",
    val bitRateKbps: Int = 160,
    val isDirectFile: Boolean = false,
    val headers: Map<String, String> = emptyMap()
)

/**
 * The single abstraction over a music backend. Adding a backend means adding one package under
 * `data/sources/` and one `@IntoSet` binding in [com.wander.android.di.SourceModule].
 *
 * Methods a source does not support must be reflected in [capabilities]; they return an empty
 * result rather than fabricating one.
 */
interface IMusicSource {
    val sourceType: SourceType
    val displayName: String
    val capabilities: SourceCapabilities

    /** Whether this source has everything it needs (credentials, permissions) to be queried. */
    val isConfigured: StateFlow<Boolean>

    suspend fun search(query: String): Result<List<UnifiedTrack>>
    suspend fun getStreamInfo(trackId: String): Result<StreamInfo>

    /**
     * One track by its id, for when another device hands over a session: the id identifies the
     * exact recording on a backend both devices share, which a title search only approximates.
     * Sources that cannot address a single track return null rather than guessing.
     */
    suspend fun getTrack(trackId: String): Result<UnifiedTrack?> = Result.success(null)

    suspend fun getLyrics(trackId: String): Result<LyricsData?> = Result.success(null)

    /**
     * The backend's own recommendation feed, shelf by shelf, for Home.
     *
     * Only meaningful when [SourceCapabilities.recommendations] is set; everything else keeps the
     * empty default rather than approximating a feed from whatever else it can reach.
     */
    suspend fun getRecommendations(): Result<List<RecommendedShelf>> = Result.success(emptyList())

    suspend fun getRadio(seedTrackId: String, count: Int = 20): Result<List<UnifiedTrack>> =
        Result.success(emptyList())

    suspend fun getLikedTracks(limit: Int = 50, offset: Int = 0): Result<List<UnifiedTrack>> =
        Result.success(emptyList())

    suspend fun getRecentTracks(limit: Int = 30): Result<List<UnifiedTrack>> =
        Result.success(emptyList())

    suspend fun getAlbums(limit: Int = 50, offset: Int = 0): Result<List<UnifiedAlbum>> =
        Result.success(emptyList())

    suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        Result.success(emptyList())

    suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> = Result.success(emptyList())
    suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        Result.success(emptyList())

    /**
     * A public link to [trackId] that anyone can open, for sharing.
     *
     * Only meaningful when [SourceCapabilities.share] is set. The default fails rather than
     * returning some other URL: a link that does not play the track is worse than no link.
     */
    suspend fun createShareLink(trackId: String, description: String): Result<String> =
        Result.failure(UnsupportedOperationException("$displayName cannot create share links"))

    suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> = Result.success(Unit)
    suspend fun scrobble(trackId: String, submissionTime: Long = System.currentTimeMillis()): Result<Unit> =
        Result.success(Unit)
}
