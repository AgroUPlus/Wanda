package com.wander.android.data.sources

import com.wander.android.data.model.LyricsData
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.SearchKind
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

    /**
     * Search restricted to one kind of content.
     *
     * Defaults to ignoring [kind] and answering with tracks, because for a pure music backend
     * every kind *is* tracks. Sources that genuinely hold other kinds — YouTube Music — override
     * this and return empty for a kind they cannot serve, rather than quietly handing back songs
     * the user did not ask for.
     */
    suspend fun search(query: String, kind: SearchKind): Result<List<UnifiedTrack>> =
        if (kind == SearchKind.TRACKS) search(query) else Result.success(emptyList())
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

    /**
     * The artist's own page — bio, portrait, and the shelves the backend arranges them into.
     *
     * Only meaningful when [SourceCapabilities.artists] is set. The default fails rather than
     * returning an empty page: a source that has no artist endpoint has nothing to say about an
     * artist, and an empty page presented as theirs would be a claim that they have no records.
     */
    suspend fun getArtist(artistId: String): Result<ArtistDetails> =
        Result.failure(UnsupportedOperationException("$sourceType has no artist pages"))

    suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> = Result.success(emptyList())
    suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> =
        Result.success(emptyList())

    /**
     * Creates a playlist holding [trackIds] and returns its id.
     *
     * Only meaningful when [SourceCapabilities.playlistWrite] is set. Like [createShareLink] the
     * default fails loudly: a playlist the user believes they made, that exists nowhere, is worse
     * than being told the source cannot make one.
     */
    suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> =
        Result.failure(UnsupportedOperationException("$displayName cannot create playlists"))

    /** Appends [trackIds] to an existing playlist. See [createPlaylist]. */
    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> =
        Result.failure(UnsupportedOperationException("$displayName cannot modify playlists"))

    /**
     * A public link to [target] that anyone can open, for sharing.
     *
     * Only meaningful when [SourceCapabilities.share] is set, and even then a source may support
     * some kinds and not others — a backend that can publish a track but has no notion of a
     * public playlist must fail for [ShareKind.PLAYLIST] rather than inventing a URL. The default
     * fails for everything, because a link that does not open what it names is worse than no link.
     */
    suspend fun createShareLink(target: ShareTarget): Result<String> =
        Result.failure(UnsupportedOperationException("$displayName cannot create share links"))

    suspend fun setLiked(trackId: String, liked: Boolean): Result<Unit> = Result.success(Unit)
    suspend fun scrobble(trackId: String, submissionTime: Long = System.currentTimeMillis()): Result<Unit> =
        Result.success(Unit)
}
