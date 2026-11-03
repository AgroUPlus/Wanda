package com.wander.android.data.repository

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.dao.TrackEmbeddingDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.ScrobbleSyncScheduler
import com.wander.android.data.model.SearchKind
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

/**
 * The single facade ViewModels talk to for music data. Room is the source of truth; sources fill it.
 */
@Singleton
class MusicRepository @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val playlistDao: PlaylistDao,
    private val remotePlaylistDao: com.wander.android.core.database.dao.RemotePlaylistDao,
    private val historyDao: HistoryDao,
    private val secureStorage: SecureStorage,
    private val connectivity: ConnectivityObserver,
    private val scrobbleSyncScheduler: ScrobbleSyncScheduler,
    private val scrobbleForwardScheduler: com.wander.android.core.sync.ScrobbleForwardScheduler,
    private val scrobbleSuppression: ScrobbleSuppression,
    private val recordingRules: RecordingRulesRepository,
    private val acousticFeatures: AcousticFeatureRepository,
    private val embeddingDao: TrackEmbeddingDao,
    private val likeOutbox: LikeOutbox,
    private val likeSyncDao: com.wander.android.core.database.dao.LikeSyncDao,
    val sources: Set<@JvmSuppressWildcards IMusicSource>
) {
    private val reachability = SourceReachability(connectivity, secureStorage)

    /** Configured sources that can answer now; see [SourceReachability] for a LAN server. */
    internal fun activeSources(): List<IMusicSource> = sources.filter { source ->
        source.isConfigured.value && reachability.isReachable(source.sourceType)
    }

    internal fun searchableSources(): List<IMusicSource> = sources.filter { source ->
        source.isSearchable.value &&
            source.capabilities.search &&
            reachability.isReachable(source.sourceType)
    }

    private suspend fun persist(tracks: List<UnifiedTrack>, asLibrary: Boolean) {
        if (tracks.isEmpty()) return
        val libraryIds = if (asLibrary) {
            tracks.filter { it.source.isPersonalLibrary }.map { it.id }
        } else {
            emptyList()
        }
        trackDao.upsertTracks(
            tracks.map { track ->
                TrackEntity.fromUnifiedTrack(track, isLibrary = track.id in libraryIds)
            }
        )
        if (libraryIds.isNotEmpty()) trackDao.markAsLibrary(libraryIds)
    }

    suspend fun rememberSharedTrack(track: UnifiedTrack) = withContext(Dispatchers.IO) {
        persist(listOf(track), asLibrary = false)
    }

    private val streamResolver = PlaybackStreamResolver(
        resources = context.resources,
        trackDao = trackDao,
        sources = sources,
        secureStorage = secureStorage,
        reachability = reachability,
        recordingRules = recordingRules
    )

    private val libraryTracks = LibraryTrackRepository(
        trackDao = trackDao,
        historyDao = historyDao,
        recordingRules = recordingRules
    )

    private val catalogCollections = CatalogCollectionRepository(
        albumDao = albumDao,
        trackDao = trackDao,
        playlistDao = playlistDao,
        sources = sources,
        activeSources = ::activeSources,
        persist = ::persist,
        recordingRules = recordingRules,
        remotePlaylists = RemotePlaylistStore(remotePlaylistDao, trackDao)
    )

    private val searchRepo = SearchAndDiscoveryRepository(
        trackDao = trackDao,
        albumDao = albumDao,
        searchableSources = ::searchableSources,
        activeSources = ::activeSources,
        sources = sources,
        persist = ::persist
    )

    private val likesAndHistory = LikesAndHistoryRepository(
        trackDao = trackDao,
        historyDao = historyDao,
        sources = sources,
        recordingRules = recordingRules,
        secureStorage = secureStorage,
        scrobbleSuppression = scrobbleSuppression,
        scrobbleSyncScheduler = scrobbleSyncScheduler,
        scrobbleForwardScheduler = scrobbleForwardScheduler,
        likeOutbox = likeOutbox
    )

    private val remoteLikes = RemoteLikesImport(likeSyncDao, ::activeSources, ::persist)

    private val radioGenerator = SmartRadioGenerator(
        trackDao = trackDao,
        activeSources = ::activeSources,
        recordingRules = recordingRules,
        acousticFeatures = acousticFeatures,
        sonicNeighbours = SonicNeighbours(embeddingDao),
        renditionsOf = { likesAndHistory.renditionsOf(it) },
        persist = ::persist
    )

    // ── Library ─────────────────────────────────────────────────────────────────────────────
    fun getLikedTracksFlow(limit: Int) = libraryTracks.getLikedTracksFlow(limit)
    fun getRecentlyPlayedFlow() = libraryTracks.getRecentlyPlayedFlow()
    suspend fun downloadedTracks() = libraryTracks.downloadedTracks()
    fun pagedHistory() = libraryTracks.pagedHistory()
    suspend fun trackById(trackId: String) = libraryTracks.trackById(trackId)

    // ── Albums & Playlists ──────────────────────────────────────────────────────────────────
    fun getRecentlyAddedAlbumIdsFlow(limit: Int = 12) = catalogCollections.getRecentlyAddedAlbumIdsFlow(limit)
    fun getAlbumTracksFlow(albumId: String) = catalogCollections.getAlbumTracksFlow(albumId)
    suspend fun refreshAlbums(pageSize: Int = CatalogCollectionRepository.ALBUM_PAGE_SIZE) = catalogCollections.refreshAlbums(pageSize)
    suspend fun importMissingAlbumTracks(limit: Int = CatalogCollectionRepository.ALBUM_IMPORT_BATCH) = catalogCollections.importMissingAlbumTracks(limit)
    suspend fun getAlbumTracks(album: UnifiedAlbum) = catalogCollections.getAlbumTracks(album)
    suspend fun getAlbumTracksById(albumId: String) = catalogCollections.getAlbumTracksById(albumId)
    suspend fun getPlaylists(only: Set<SourceType>? = null) = catalogCollections.getPlaylists(only)
    fun observeRemotePlaylists() = catalogCollections.observeRemotePlaylists()
    suspend fun getPlaylistTracks(playlist: UnifiedPlaylist) = catalogCollections.getPlaylistTracks(playlist)
    suspend fun getPlaylistById(playlistId: String) = catalogCollections.getPlaylistById(playlistId)
    suspend fun getPlaylistTracksById(playlistId: String) = catalogCollections.getPlaylistTracksById(playlistId)

    // ── Playback & Streams ──────────────────────────────────────────────────────────────────
    fun registerEphemeralStream(trackId: String, info: StreamInfo) = streamResolver.registerEphemeralStream(trackId, info)
    fun clearEphemeralStreams() = streamResolver.clearEphemeralStreams()
    suspend fun getStreamInfo(trackId: String) = streamResolver.getStreamInfo(trackId)

    /** The clip shown in place of the cover in Video mode, from the track's own backend. */
    suspend fun getVideoStreamInfo(track: UnifiedTrack): Result<StreamInfo> = withContext(Dispatchers.IO) {
        sources.firstOrNull { it.sourceType == track.source && it.capabilities.videoClips }
            ?.getVideoStreamInfo(track.id)
            ?: Result.failure(UnsupportedOperationException("${track.source.displayName} has no video clips"))
    }

    // ── Search & Discovery ──────────────────────────────────────────────────────────────────
    suspend fun searchAllSources(query: String, onlySources: Set<SourceType>? = null, kind: SearchKind = SearchKind.TRACKS) =
        searchRepo.searchAllSources(query, onlySources, kind)
    suspend fun searchWithStatus(query: String, onlySources: Set<SourceType>?, kind: SearchKind): SearchOutcome =
        searchRepo.searchWithStatus(query, onlySources, kind)
    suspend fun searchAlbums(query: String) = searchRepo.searchAlbums(query)
    suspend fun resolveTrack(id: String, title: String, artist: String) = searchRepo.resolveTrack(id, title, artist)
    suspend fun getRecentTracks(limit: Int = 30) = searchRepo.getRecentTracks(limit)
    fun configuredSources(): List<SourceType> =
        activeSources().filter { it.capabilities.holdsTracks }.map { it.sourceType }

    // ── Likes & History ─────────────────────────────────────────────────────────────────────
    val writeErrors: SharedFlow<String> = likeOutbox.failures
    fun getLikedTrackIdsFlow(): Flow<Set<String>> = likesAndHistory.getLikedTrackIdsFlow()
    suspend fun toggleLike(track: UnifiedTrack) = likesAndHistory.toggleLike(track)
    suspend fun like(track: UnifiedTrack) = likesAndHistory.setLiked(track, liked = true)
    suspend fun unifySplitLikes() = likesAndHistory.unifySplitLikes()

    /** Every connected backend's liked songs into Room, then any like still owed sent on. */
    suspend fun importRemoteLikes() = withContext(Dispatchers.IO) {
        remoteLikes.importAll()
        likeOutbox.resumeIfOwed()
    }
    suspend fun recordPlay(track: UnifiedTrack) = likesAndHistory.recordPlay(track)
    suspend fun removeFromHistory(historyId: Long) = likesAndHistory.removeFromHistory(historyId)
    suspend fun clearHistory() = likesAndHistory.clearHistory()

    // ── Radio ───────────────────────────────────────────────────────────────────────────────
    suspend fun generateRadio(seed: UnifiedTrack, count: Int = 20) = radioGenerator.generateRadio(seed, count)
}
