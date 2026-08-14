package com.wander.android.data.repository

import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only thing ViewModels talk to for music data. Room is the source of truth; sources fill it.
 */
@Singleton
class MusicRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val historyDao: HistoryDao,
    private val secureStorage: SecureStorage,
    val sources: Set<@JvmSuppressWildcards IMusicSource>
) {
    /** Sources that are configured *and* not muted by offline mode. */
    private fun activeSources(): List<IMusicSource> {
        val offline = secureStorage.isOfflineMode.value
        return sources.filter { source ->
            source.isConfigured.value && (!offline || source.sourceType == SourceType.LOCAL)
        }
    }

    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    // ── Library reads (always from Room, so they work offline) ──────────────────────────────

    fun getAllTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getAllTracksFlow().mapToTracks()

    fun getLikedTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getLikedTracksFlow().mapToTracks()

    fun getDownloadedTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getDownloadedTracksFlow().mapToTracks()

    fun getTracksBySourceFlow(source: SourceType): Flow<List<UnifiedTrack>> =
        trackDao.getTracksBySourceFlow(source).mapToTracks()

    fun getAlbumsFlow(): Flow<List<UnifiedAlbum>> =
        albumDao.getAllAlbumsFlow().map { list -> list.map(AlbumEntity::toUnifiedAlbum) }

    fun getAlbumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByAlbumFlow(albumId).mapToTracks()

    private fun Flow<List<TrackEntity>>.mapToTracks() =
        map { list -> list.map(TrackEntity::toUnifiedTrack) }

    // ── Playback ────────────────────────────────────────────────────────────────────────────

    /** Called by [com.wander.android.core.playback.StreamResolver] at load time. */
    suspend fun getStreamInfo(trackId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        val cached = trackDao.getTrackById(trackId)
        cached?.localFilePath?.let { path ->
            return@withContext Result.success(StreamInfo(uri = path, isDirectFile = true))
        }
        val type = cached?.source ?: SourceType.entries.firstOrNull {
            trackId.startsWith(it.idPrefix)
        } ?: return@withContext Result.failure(
            IllegalArgumentException("Unrecognised track id: $trackId")
        )
        val source = sourceFor(type)
            ?: return@withContext Result.failure(IllegalStateException("$type is unavailable"))
        source.getStreamInfo(trackId)
    }

    /**
     * Increments the play count and queues a scrobble. In incognito mode neither happens — the
     * user's listening simply is not recorded.
     */
    suspend fun recordPlay(track: UnifiedTrack) = withContext(Dispatchers.IO) {
        if (secureStorage.isIncognitoMode) return@withContext
        trackDao.incrementPlayCount(track.id, System.currentTimeMillis())
        val entryId = historyDao.recordHistory(HistoryEntity(trackId = track.id))
        val scrobbled = sourceFor(track.source)
            ?.takeIf { it.capabilities.scrobble }
            ?.scrobble(track.id)
            ?.isSuccess == true
        if (scrobbled) historyDao.markScrobbled(listOf(entryId))
    }

    // ── Persistence ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists fetched tracks so they are available offline next time.
     *
     * [asLibrary] separates "this is the user's collection" from "this is something they merely
     * looked at". Browsing your own Navidrome albums is the former; a search hit, a radio pick or
     * an Internet Archive result is the latter. Only the former reaches the Library screen — which
     * is what stops typing in Search from growing the library.
     *
     * Sources whose catalogue is not personal ([SourceType.isPersonalLibrary]) never count as
     * library, whichever path fetched them.
     */
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

    // ── Searching ───────────────────────────────────────────────────────────────────────────

    /**
     * Room first so results appear instantly, then every active source in parallel. Results are
     * cached but never enter the library, and duplicates of the same recording across backends
     * collapse to the best-ranked source.
     */
    suspend fun searchAllSources(query: String): List<UnifiedTrack> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        val cached = trackDao.searchTracks(query).map(TrackEntity::toUnifiedTrack)
        val remote = activeSources()
            .filter { it.capabilities.search }
            .map { source -> async { source.search(query).getOrDefault(emptyList()) } }
            .flatMap { it.await() }

        persist(remote, asLibrary = false)
        TrackDeduplicator.deduplicate((cached + remote).distinctBy { it.id })
    }

    // ── Writes ──────────────────────────────────────────────────────────────────────────────

    suspend fun toggleLike(track: UnifiedTrack): Result<Unit> = withContext(Dispatchers.IO) {
        val liked = !track.isLiked
        trackDao.setLiked(track.id, liked)
        val source = sourceFor(track.source)
        if (source == null || !source.capabilities.likes) return@withContext Result.success(Unit)
        source.setLiked(track.id, liked).onFailure { trackDao.setLiked(track.id, !liked) }
    }

    // ── Remote browsing ─────────────────────────────────────────────────────────────────────

    suspend fun refreshAlbums(limit: Int = 50): List<UnifiedAlbum> = coroutineScope {
        val albums = activeSources()
            .filter { it.capabilities.albums }
            .map { source -> async { source.getAlbums(limit).getOrDefault(emptyList()) } }
            .flatMap { it.await() }
        if (albums.isNotEmpty()) {
            albumDao.insertAlbums(albums.map(AlbumEntity::fromUnifiedAlbum))
        }
        albums
    }

    suspend fun getAlbumTracks(album: UnifiedAlbum): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        val tracks = sourceFor(album.source)?.getAlbumTracks(album.id)?.getOrDefault(emptyList())
        if (tracks.isNullOrEmpty()) {
            trackDao.getTracksInAlbum(album.id).map(TrackEntity::toUnifiedTrack)
        } else {
            // Browsing an album on your own server is browsing your own collection.
            persist(tracks, asLibrary = true)
            tracks
        }
    }

    suspend fun getPlaylists(): List<UnifiedPlaylist> = coroutineScope {
        activeSources()
            .filter { it.capabilities.playlists }
            .map { source -> async { source.getPlaylists().getOrDefault(emptyList()) } }
            .flatMap { it.await() }
    }

    suspend fun getPlaylistTracks(playlist: UnifiedPlaylist): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val tracks = sourceFor(playlist.source)
                ?.getPlaylistTracks(playlist.id)
                ?.getOrDefault(emptyList())
                .orEmpty()
            persist(tracks, asLibrary = true)
            tracks
        }

    suspend fun getRecentTracks(limit: Int = 30): List<UnifiedTrack> = coroutineScope {
        val remote = activeSources()
            .map { source -> async { source.getRecentTracks(limit).getOrDefault(emptyList()) } }
            .flatMap { it.await() }
        persist(remote, asLibrary = true)
        TrackDeduplicator
            .deduplicate(
                (remote + trackDao.getRecentlyAddedTracks(limit).map(TrackEntity::toUnifiedTrack))
                    .distinctBy { it.id }
            )
            .take(limit)
    }

    suspend fun getRecentlyPlayed(limit: Int = 20): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getRecentlyPlayedTracks(limit).map(TrackEntity::toUnifiedTrack)
    }

    suspend fun getTopTracks(limit: Int = 20): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getTopPlayedTracks(limit).map(TrackEntity::toUnifiedTrack)
    }

    suspend fun getLikedTracks(limit: Int = 20): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getLikedTracksList(limit).map(TrackEntity::toUnifiedTrack)
    }

    /** In the library but never listened to. */
    suspend fun getNeverPlayed(limit: Int = 20): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getNeverPlayedTracks(limit).map(TrackEntity::toUnifiedTrack)
    }

    /** Recently added, restricted to one backend, for Home's per-source shelves. */
    suspend fun getRecentBySource(source: SourceType, limit: Int = 12): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            trackDao.getRecentlyAddedInSource(source, limit).map(TrackEntity::toUnifiedTrack)
        }

    /**
     * Recently played, one track per album, so the shelf reads as "records you were listening to"
     * rather than repeating six tracks off the same one.
     */
    suspend fun getRecentAlbumStarters(limit: Int = 12): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            trackDao.getRecentlyPlayedTracks(limit * 4)
                .map(TrackEntity::toUnifiedTrack)
                .distinctBy { it.album?.takeIf { name -> name.isNotBlank() } ?: it.id }
                .take(limit)
        }

    /** The backends the user actually has set up, for building per-source Home shelves. */
    fun configuredSources(): List<SourceType> = activeSources().map { it.sourceType }

    /**
     * Endless radio. Falls back to the user's own most-played tracks when the source has no
     * similarity API — that is a real playlist, not a placeholder.
     */
    suspend fun generateRadio(seed: UnifiedTrack, count: Int = 20): List<UnifiedTrack> =
        withContext(Dispatchers.IO) {
            val source = sourceFor(seed.source)?.takeIf { it.capabilities.radio }
            val tracks = source?.getRadio(seed.id, count)?.getOrNull().orEmpty()
            if (tracks.isNotEmpty()) {
                persist(tracks, asLibrary = false)
                tracks
            } else {
                trackDao.getTopPlayedTracks(count * 2)
                    .map(TrackEntity::toUnifiedTrack)
                    .filter { it.id != seed.id }
                    .shuffled()
                    .take(count)
            }
        }
}
