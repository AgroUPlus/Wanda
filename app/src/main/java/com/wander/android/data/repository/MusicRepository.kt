package com.wander.android.data.repository

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
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
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao,
    private val secureStorage: SecureStorage,
    private val connectivity: ConnectivityObserver,
    private val scrobbleSyncScheduler: ScrobbleSyncScheduler,
    private val scrobbleSuppression: ScrobbleSuppression,
    private val recordingRules: RecordingRulesRepository,
    private val acousticFeatures: AcousticFeatureRepository,
    val sources: Set<@JvmSuppressWildcards IMusicSource>
) {
    internal fun activeSources(): List<IMusicSource> {
        val offline = secureStorage.isOfflineMode.value || !connectivity.isOnline.value
        return sources.filter { source ->
            source.isConfigured.value && (!offline || source.sourceType == SourceType.LOCAL)
        }
    }

    internal fun searchableSources(): List<IMusicSource> {
        val offline = secureStorage.isOfflineMode.value || !connectivity.isOnline.value
        return sources.filter { source ->
            source.isSearchable.value &&
                source.capabilities.search &&
                (!offline || source.sourceType == SourceType.LOCAL)
        }
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
        trackDao = trackDao,
        sources = sources,
        secureStorage = secureStorage,
        connectivity = connectivity,
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
        recordingRules = recordingRules
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
        scrobbleSyncScheduler = scrobbleSyncScheduler
    )

    private val radioGenerator = SmartRadioGenerator(
        trackDao = trackDao,
        activeSources = ::activeSources,
        recordingRules = recordingRules,
        acousticFeatures = acousticFeatures,
        renditionsOf = { likesAndHistory.renditionsOf(it) },
        persist = ::persist
    )

    // ── Library ─────────────────────────────────────────────────────────────────────────────
    fun getLikedTracksFlow() = libraryTracks.getLikedTracksFlow()
    fun getRecentlyPlayedFlow() = libraryTracks.getRecentlyPlayedFlow()
    fun getDownloadedTracksFlow() = libraryTracks.getDownloadedTracksFlow()
    suspend fun downloadedTracks() = libraryTracks.downloadedTracks()
    fun pagedLibraryTracks(source: SourceType?) = libraryTracks.pagedLibraryTracks(source)
    suspend fun tracksByIds(ids: List<String>) = libraryTracks.tracksByIds(ids)
    suspend fun libraryTrackIds(source: SourceType?) = libraryTracks.libraryTrackIds(source)
    suspend fun trackById(trackId: String) = libraryTracks.trackById(trackId)
    suspend fun deleteDownloadedTrack(trackId: String) = libraryTracks.deleteDownloadedTrack(trackId)

    // ── Albums & Playlists ──────────────────────────────────────────────────────────────────
    fun getAlbumsFlow() = catalogCollections.getAlbumsFlow()
    fun getRecentlyAddedAlbumIdsFlow(limit: Int = 12) = catalogCollections.getRecentlyAddedAlbumIdsFlow(limit)
    fun getAlbumTracksFlow(albumId: String) = catalogCollections.getAlbumTracksFlow(albumId)
    suspend fun refreshAlbums(pageSize: Int = CatalogCollectionRepository.ALBUM_PAGE_SIZE) = catalogCollections.refreshAlbums(pageSize)
    suspend fun importMissingAlbumTracks(limit: Int = CatalogCollectionRepository.ALBUM_IMPORT_BATCH) = catalogCollections.importMissingAlbumTracks(limit)
    suspend fun getAlbumTracks(album: UnifiedAlbum) = catalogCollections.getAlbumTracks(album)
    suspend fun getAlbumTracksById(albumId: String) = catalogCollections.getAlbumTracksById(albumId)
    suspend fun getPlaylists() = catalogCollections.getPlaylists()
    suspend fun getPlaylistTracks(playlist: UnifiedPlaylist) = catalogCollections.getPlaylistTracks(playlist)
    suspend fun getPlaylistById(playlistId: String) = catalogCollections.getPlaylistById(playlistId)
    suspend fun getPlaylistTracksById(playlistId: String) = catalogCollections.getPlaylistTracksById(playlistId)

    // ── Playback & Streams ──────────────────────────────────────────────────────────────────
    fun registerEphemeralStream(trackId: String, info: StreamInfo) = streamResolver.registerEphemeralStream(trackId, info)
    fun clearEphemeralStreams() = streamResolver.clearEphemeralStreams()
    suspend fun getStreamInfo(trackId: String) = streamResolver.getStreamInfo(trackId)

    // ── Search & Discovery ──────────────────────────────────────────────────────────────────
    suspend fun searchAllSources(query: String, onlySources: Set<SourceType>? = null, kind: SearchKind = SearchKind.TRACKS) =
        searchRepo.searchAllSources(query, onlySources, kind)
    suspend fun searchAlbums(query: String) = searchRepo.searchAlbums(query)
    suspend fun resolveTrack(id: String, title: String, artist: String) = searchRepo.resolveTrack(id, title, artist)
    suspend fun getRecentTracks(limit: Int = 30) = searchRepo.getRecentTracks(limit)
    fun configuredSources(): List<SourceType> = activeSources().map { it.sourceType }

    // ── Likes & History ─────────────────────────────────────────────────────────────────────
    val writeErrors: SharedFlow<String> = likesAndHistory.writeErrors
    fun getLikedTrackIdsFlow(): Flow<Set<String>> = likesAndHistory.getLikedTrackIdsFlow()
    suspend fun toggleLike(track: UnifiedTrack) = likesAndHistory.toggleLike(track)
    suspend fun unifySplitLikes() = likesAndHistory.unifySplitLikes()
    suspend fun recordPlay(track: UnifiedTrack) = likesAndHistory.recordPlay(track)

    // ── Radio ───────────────────────────────────────────────────────────────────────────────
    suspend fun generateRadio(seed: UnifiedTrack, count: Int = 20) = radioGenerator.generateRadio(seed, count)
}
