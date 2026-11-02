package com.wander.android.data.sources.deezer

import com.wander.android.data.model.ArtistDetails
import com.wander.android.data.model.RecommendedShelf
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [IMusicSource] implementation for Deezer.
 *
 * Provides search, artist profiles, albums, radio, and charts.
 * Streams are resolved dynamically based on user account tier (Free vs. Premium/HiFi)
 * and decrypted via [com.wander.android.core.playback.DeezerDecryptingDataSource].
 */
@Singleton
class DeezerSource @Inject constructor(
    private val apiClient: DeezerApiClient,
    private val streamResolver: DeezerStreamResolver,
    private val accountManager: DeezerAccountManager
) : IMusicSource {

    override val sourceType: SourceType = SourceType.DEEZER

    override val displayName: String = "Deezer"

    override val capabilities: SourceCapabilities = SourceCapabilities(
        search = true,
        albums = true,
        artists = true,
        playlists = false,
        likes = false,
        radio = true,
        recommendations = true
    )

    /**
     * Also what [isSearchable] and [isStreamable] answer. Deezer's public API would serve a search
     * signed out, but a stream needs the account's session — and every search result is something
     * the app goes on to play: the search screen, link and import matching, deduplication picking
     * the copy to keep. A signed-out Deezer in search handed out tracks that could only fail.
     */
    override val isConfigured: StateFlow<Boolean> = accountManager.isLoggedIn

    override suspend fun search(query: String): Result<List<UnifiedTrack>> =
        apiClient.searchTracks(query)

    // No `search(query, kind)` override: Deezer is a pure music backend, and `IMusicSource`'s own
    // default already answers exactly that way — `search(query)` for TRACKS, empty for every other
    // kind. Album and artist search go through their own dedicated path instead —
    // `SearchAndDiscoveryRepository.searchAlbums`, which derives albums from track results rather
    // than asking a source for a kind — `SearchKind` itself has no ALBUMS/ARTISTS case to route.

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> =
        streamResolver.resolveStream(trackId)

    override suspend fun getTrack(trackId: String): Result<UnifiedTrack?> =
        apiClient.getTrack(trackId)

    override suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        apiClient.getAlbumTracks(albumId)

    override suspend fun getArtist(artistId: String): Result<ArtistDetails> =
        apiClient.getArtist(artistId)

    override suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        apiClient.getTrackRadio(seedTrackId)

    override suspend fun getRecommendations(): Result<List<RecommendedShelf>> =
        apiClient.getChartTracks().map { tracks ->
            if (tracks.isNotEmpty()) {
                listOf(RecommendedShelf(id = "deezer_charts", title = "Deezer Top Charts", tracks = tracks))
            } else {
                emptyList()
            }
        }
}
