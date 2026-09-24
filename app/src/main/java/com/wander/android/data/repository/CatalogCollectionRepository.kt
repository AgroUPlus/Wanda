package com.wander.android.data.repository

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages albums and playlists synchronization, querying, and import.
 */
internal class CatalogCollectionRepository(
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val sources: Set<IMusicSource>,
    private val activeSources: () -> List<IMusicSource>,
    private val persist: suspend (List<UnifiedTrack>, Boolean) -> Unit,
    private val recordingRules: RecordingRulesRepository
) {
    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    fun getAlbumsFlow(): Flow<List<UnifiedAlbum>> =
        albumDao.getLibraryAlbumsFlow()
            .map { list -> list.map(AlbumEntity::toUnifiedAlbum) }
            .flowOn(Dispatchers.Default)

    fun getRecentlyAddedAlbumIdsFlow(limit: Int = 12): Flow<List<String>> =
        trackDao.observeRecentlyAddedAlbumIds(limit)

    fun getAlbumTracksFlow(albumId: String): Flow<List<UnifiedTrack>> =
        trackDao.getTracksByAlbumFlow(albumId)
            .map { list -> list.map(TrackEntity::toUnifiedTrack) }
            .flowOn(Dispatchers.Default)

    suspend fun refreshAlbums(pageSize: Int = ALBUM_PAGE_SIZE): List<UnifiedAlbum> = coroutineScope {
        val albums = activeSources()
            .filter { it.capabilities.albums }
            .map { source ->
                async {
                    val collected = ArrayList<UnifiedAlbum>()
                    val seen = HashSet<String>()
                    var offset = 0
                    while (collected.size < MAX_LIBRARY_ALBUMS) {
                        val page = source.getAlbums(pageSize, offset).getOrDefault(emptyList())
                        if (page.isEmpty()) break
                        val fresh = page.filter { seen.add(it.id) }
                        collected += fresh
                        if (fresh.isEmpty() || page.size < pageSize) break
                        offset += page.size
                    }
                    collected
                }
            }
            .flatMap { it.await() }
        if (albums.isNotEmpty()) {
            albumDao.insertAlbums(albums.map { AlbumEntity.fromUnifiedAlbum(it, isLibrary = true) })
        }
        albums
    }

    suspend fun importMissingAlbumTracks(limit: Int = ALBUM_IMPORT_BATCH): Int =
        withContext(Dispatchers.IO) {
            val empty = albumDao.libraryAlbumsWithoutTracks(limit)
            if (empty.isEmpty()) return@withContext 0
            for (entity in empty) getAlbumTracks(entity.toUnifiedAlbum())

            val stillEmpty = albumDao.libraryAlbumsWithoutTracks(limit).mapTo(HashSet()) { it.id }
            empty.count { it.id !in stillEmpty }
        }

    suspend fun getAlbumTracks(album: UnifiedAlbum): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        val tracks = sourceFor(album.source)?.getAlbumTracks(album.id)?.getOrDefault(emptyList())
        if (tracks.isNullOrEmpty()) {
            trackDao.getTracksInAlbum(album.id).map(TrackEntity::toUnifiedTrack)
        } else {
            persist(tracks, true)
            tracks
        }
    }

    suspend fun getAlbumTracksById(albumId: String): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        val type = SourceType.entries.firstOrNull { albumId.startsWith(it.idPrefix) }
            ?: return@withContext emptyList()
        val tracks = sourceFor(type)?.getAlbumTracks(albumId)?.getOrDefault(emptyList())
        if (tracks.isNullOrEmpty()) {
            trackDao.getTracksInAlbum(albumId).map(TrackEntity::toUnifiedTrack)
        } else {
            persist(tracks, false)
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
            persist(tracks, true)
            tracks
        }

    suspend fun getPlaylistById(playlistId: String): UnifiedPlaylist? = withContext(Dispatchers.IO) {
        val type = SourceType.entries.firstOrNull { playlistId.startsWith(it.idPrefix) }
        val remote = type?.let(::sourceFor)?.getPlaylists()?.getOrNull()?.firstOrNull { it.id == playlistId }
        if (remote != null) return@withContext remote

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
        getPlaylists().firstOrNull { it.id == playlistId }
    }

    suspend fun getPlaylistTracksById(playlistId: String): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        val type = SourceType.entries.firstOrNull { playlistId.startsWith(it.idPrefix) } ?: SourceType.LOCAL
        val tracks = sourceFor(type)?.getPlaylistTracks(playlistId)?.getOrDefault(emptyList()).orEmpty()
        if (tracks.isNotEmpty()) {
            persist(tracks, true)
            return@withContext tracks
        }
        val localEntity = playlistDao.getPlaylistById(playlistId)
        if (localEntity != null) {
            val ids = localEntity.trackIds.split(',').filter { it.isNotBlank() }
            val tracksById = trackDao.getTracksByIds(ids).associateBy { it.id }
            val baseTracks = ids.mapNotNull { id -> tracksById[id]?.toUnifiedTrack() }
            val downloadedTracks = trackDao.getOfflineTracksOnce().map(TrackEntity::toUnifiedTrack)
            val rules = recordingRules.current()
            return@withContext baseTracks.map { track ->
                if (track.isPlayableOffline()) return@map track
                rules.substituteFor(track, downloadedTracks) ?: track
            }
        }
        emptyList()
    }

    companion object {
        const val ALBUM_PAGE_SIZE = 200
        const val MAX_LIBRARY_ALBUMS = 5_000
        const val ALBUM_IMPORT_BATCH = 40
    }
}
