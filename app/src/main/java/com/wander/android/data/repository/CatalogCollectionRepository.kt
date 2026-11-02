package com.wander.android.data.repository

import com.wander.android.core.database.dao.getTracksByIdsChunked
import com.wander.android.core.database.dao.AlbumDao
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.AlbumEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isPlayableOffline
import com.wander.android.data.sources.IMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages albums and playlists synchronization, querying, and import.
 *
 * Loaders answer with a [Result]. A backend that fails is a failure, never an empty album or
 * playlist; Room's copy is the answer instead only when it actually has one, which is what lets
 * a record opened before still play offline.
 */
internal class CatalogCollectionRepository(
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val sources: Set<IMusicSource>,
    private val activeSources: () -> List<IMusicSource>,
    private val persist: suspend (List<UnifiedTrack>, Boolean) -> Unit,
    private val recordingRules: RecordingRulesRepository,
    private val remotePlaylists: RemotePlaylistStore
) {
    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    fun getRecentlyAddedAlbumIdsFlow(limit: Int = 12): Flow<List<String>> =
        trackDao.observeRecentlyAddedAlbumIds(limit)

    fun getAlbumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByAlbumFlow(albumId)
            .map { list -> list.map(TrackEntity::toUnifiedTrack) }
            .flowOn(Dispatchers.Default)

    /** Every source's albums, written to Room. Fails only when a source failed and none answered. */
    suspend fun refreshAlbums(pageSize: Int = ALBUM_PAGE_SIZE): Result<List<UnifiedAlbum>> = coroutineScope {
        val results = activeSources()
            .filter { it.capabilities.albums }
            .map { source -> async { collectAlbums(source, pageSize) } }
            .awaitAll()
        val albums = results.flatMap { it.getOrNull().orEmpty() }
        if (albums.isNotEmpty()) {
            albumDao.insertAlbums(albums.map { AlbumEntity.fromUnifiedAlbum(it, isLibrary = true) })
        }
        combined(results, albums)
    }

    /**
     * Pages through one source's albums until it runs dry, repeats itself, or hits the library cap.
     * A page that fails ends the walk: a failure if nothing came before it, else what did.
     */
    private suspend fun collectAlbums(source: IMusicSource, pageSize: Int): Result<List<UnifiedAlbum>> {
        val collected = ArrayList<UnifiedAlbum>()
        val seen = HashSet<String>()
        var offset = 0
        while (collected.size < MAX_LIBRARY_ALBUMS) {
            val page = source.getAlbums(pageSize, offset).getOrElse { error ->
                return if (collected.isEmpty()) Result.failure(error) else Result.success(collected)
            }
            if (page.isEmpty()) break
            val fresh = page.filter { seen.add(it.id) }
            collected += fresh
            if (fresh.isEmpty() || page.size < pageSize) break
            offset += page.size
        }
        return Result.success(collected)
    }

    suspend fun importMissingAlbumTracks(limit: Int = ALBUM_IMPORT_BATCH): Int =
        withContext(Dispatchers.IO) {
            val empty = albumDao.libraryAlbumsWithoutTracks(limit)
            if (empty.isEmpty()) return@withContext 0
            for (entity in empty) getAlbumTracks(entity.toUnifiedAlbum())

            val stillEmpty = albumDao.libraryAlbumsWithoutTracks(limit).mapTo(HashSet()) { it.id }
            empty.count { it.id !in stillEmpty }
        }

    suspend fun getAlbumTracks(album: UnifiedAlbum): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        loadAlbumTracks(album.id, sourceFor(album.source), asLibrary = true)
    }

    suspend fun getAlbumTracksById(albumId: String): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val type = SourceType.entries.firstOrNull { albumId.startsWith(it.idPrefix) }
            ?: return@withContext Result.success(emptyList())
        loadAlbumTracks(albumId, sourceFor(type), asLibrary = false)
    }

    /** The backend's tracks, persisted; Room's when the backend has none or fails and Room has some. */
    private suspend fun loadAlbumTracks(albumId: String, source: IMusicSource?, asLibrary: Boolean): Result<List<UnifiedTrack>> {
        val remote = source?.getAlbumTracks(albumId)
        val tracks = remote?.getOrNull()
        if (!tracks.isNullOrEmpty()) {
            persist(tracks, asLibrary)
            return Result.success(tracks)
        }
        val stored = trackDao.getTracksInAlbum(albumId).map(TrackEntity::toUnifiedTrack)
        val failure = remote?.exceptionOrNull()
        return if (stored.isEmpty() && failure != null) Result.failure(failure) else Result.success(stored)
    }

    /**
     * Every source's playlists, or only [only]'s: the library re-reads the local ones alone.
     * Fails only when a source failed and none answered with anything. A backend's answer is
     * written to Room, which is where the library reads it from — see [observeRemotePlaylists].
     */
    suspend fun getPlaylists(only: Set<SourceType>? = null): Result<List<UnifiedPlaylist>> = coroutineScope {
        val asked = activeSources()
            .filter { it.capabilities.playlists && (only == null || it.sourceType in only) }
        val results = asked.map { source -> async { source.getPlaylists() } }.awaitAll()
        asked.zip(results).forEach { (source, result) ->
            if (source.sourceType in RemotePlaylistStore.ROOM_NATIVE) return@forEach
            result.onSuccess { remotePlaylists.replace(source.sourceType, it) }
        }
        combined(results, results.flatMap { it.getOrNull().orEmpty() })
    }

    /** Every backend playlist Room holds, again whenever a refresh changes them. */
    fun observeRemotePlaylists(): Flow<List<UnifiedPlaylist>> = remotePlaylists.observe()

    suspend fun getPlaylistTracks(playlist: UnifiedPlaylist): Result<List<UnifiedTrack>> =
        withContext(Dispatchers.IO) {
            val remote = sourceFor(playlist.source)?.getPlaylistTracks(playlist.id)
                ?: return@withContext Result.success(emptyList())
            remembered(playlist.id, playlist.source, remote)
        }

    /**
     * A backend's tracks for a playlist, persisted with the playlist's order; when the backend
     * fails, the order it last answered with, if this device ever opened the playlist.
     */
    private suspend fun remembered(
        playlistId: String,
        type: SourceType,
        remote: Result<List<UnifiedTrack>>
    ): Result<List<UnifiedTrack>> {
        val tracks = remote.getOrElse { error ->
            val stored = if (type in RemotePlaylistStore.ROOM_NATIVE) null else remotePlaylists.tracks(playlistId)
            return stored?.let { Result.success(it) } ?: Result.failure(error)
        }
        // A followed playlist's tracks are already in Room, and are someone else's picks:
        // persisting them as library would put them in this user's own collection.
        if (type != SourceType.AGRO) persist(tracks, true)
        if (type !in RemotePlaylistStore.ROOM_NATIVE) remotePlaylists.rememberTracks(playlistId, tracks)
        return Result.success(tracks)
    }

    suspend fun getPlaylistById(playlistId: String): UnifiedPlaylist? = withContext(Dispatchers.IO) {
        val type = SourceType.entries.firstOrNull { playlistId.startsWith(it.idPrefix) }
        val remote = type?.let(::sourceFor)?.getPlaylist(playlistId)?.getOrNull()
        if (remote != null) return@withContext remote
        if (type != null && type !in RemotePlaylistStore.ROOM_NATIVE) {
            remotePlaylists.playlist(playlistId)?.let { return@withContext it }
        }

        val localEntity = playlistDao.getPlaylistById(playlistId)
        if (localEntity != null) {
            val firstTrackId = localEntity.trackIds.split(',').firstOrNull { it.isNotBlank() }
            val fallbackCover = if (localEntity.coverArtUrl.isNullOrBlank() && firstTrackId != null) {
                trackDao.getTrackById(firstTrackId)?.artworkUrl
            } else {
                localEntity.coverArtUrl
            }
            return@withContext localEntity.toUnifiedPlaylist().copy(coverArtUrl = fallbackCover)
        }
        // Null for a playlist none of them has; their failure is the screen's to report, via the tracks.
        getPlaylists().getOrNull()?.firstOrNull { it.id == playlistId }
    }

    suspend fun getPlaylistTracksById(playlistId: String): Result<List<UnifiedTrack>> = withContext(Dispatchers.IO) {
        val type = SourceType.entries.firstOrNull { playlistId.startsWith(it.idPrefix) } ?: SourceType.LOCAL
        val remote = sourceFor(type)?.getPlaylistTracks(playlistId)
        if (remote != null && type !in RemotePlaylistStore.ROOM_NATIVE) {
            return@withContext remembered(playlistId, type, remote)
        }
        val tracks = remote?.getOrNull().orEmpty()
        if (tracks.isNotEmpty()) return@withContext remembered(playlistId, type, Result.success(tracks))
        val localEntity = playlistDao.getPlaylistById(playlistId)
        if (localEntity != null) {
            val ids = localEntity.trackIds.split(',').filter { it.isNotBlank() }
            val tracksById = trackDao.getTracksByIdsChunked(ids).associateBy { it.id }
            val baseTracks = ids.mapNotNull { id -> tracksById[id]?.toUnifiedTrack() }
            val downloadedTracks = trackDao.getOfflineTracksOnce().map(TrackEntity::toUnifiedTrack)
            val rules = recordingRules.current()
            return@withContext Result.success(
                baseTracks.map { track ->
                    if (track.isPlayableOffline()) return@map track
                    rules.substituteFor(track, downloadedTracks) ?: track
                }
            )
        }
        remote?.exceptionOrNull()?.let { return@withContext Result.failure(it) }
        Result.success(emptyList())
    }

    /** [items] gathered from several sources: a failure only when one failed and nothing came back. */
    private fun <T> combined(results: List<Result<List<T>>>, items: List<T>): Result<List<T>> {
        val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        return if (failure != null && items.isEmpty()) Result.failure(failure) else Result.success(items)
    }

    companion object {
        const val ALBUM_PAGE_SIZE = 200
        const val MAX_LIBRARY_ALBUMS = 5_000
        const val ALBUM_IMPORT_BATCH = 40
    }
}
