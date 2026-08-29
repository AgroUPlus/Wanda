package com.wander.android.data.sources.local

import android.content.Context
import com.wander.android.core.database.dao.PlaylistDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.PlaylistEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.permissions.hasAudioPermission
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import com.wander.android.data.sources.SourceCapabilities
import com.wander.android.data.sources.StreamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device audio, via MediaStore.
 *
 * The scan writes into Room once and every read comes back out of Room.
 */
@Singleton
class LocalMusicSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scanner: MediaStoreScanner,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val secureStorage: SecureStorage
) : IMusicSource {

    override val sourceType = SourceType.LOCAL
    override val displayName = "On this device"

    override val capabilities = SourceCapabilities(
        search = true,
        albums = true,
        radio = true,
        playlists = true,
        playlistWrite = true
    )

    private val _isConfigured = MutableStateFlow(context.hasAudioPermission())
    override val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val scanMutex = Mutex()

    /** Call after the audio permission is granted, and from the Settings "rescan" action. */
    suspend fun refresh(full: Boolean = false) = scanMutex.withLock {
        _isConfigured.value = context.hasAudioPermission()
        if (!_isConfigured.value) return@withLock

        val since = if (full) 0L else secureStorage.localScanWatermark
        val scan = scanner.scan(since)
        if (full) trackDao.clearBySource(SourceType.LOCAL)
        if (scan.tracks.isNotEmpty()) {
            // A scanned file is by definition part of the user's collection.
            trackDao.upsertTracks(
                scan.tracks.map { TrackEntity.fromUnifiedTrack(it, isLibrary = true) }
            )
            trackDao.markAsLibrary(scan.tracks.map { it.id })
        }
        prune()
        secureStorage.localScanWatermark = scan.watermarkSeconds
    }

    /**
     * Forgets local tracks whose file is no longer on the device.
     *
     * An incremental scan only ever *adds*: it asks MediaStore for files modified since the last
     * watermark, so a file that has been deleted is simply absent from the answer and its row
     * stays in Room forever. Until this existed the only cure was a full rescan, which throws the
     * whole local library away and rebuilds it.
     *
     * It bit hardest after a peer-to-peer fetch. Each download wrote a new MediaStore row, and
     * when an older row went the library kept pointing at it — playback then failed with
     * `FileNotFoundException: No item at content://media/external/audio/media/470` on a song whose
     * file was sitting on disk perfectly well under a different id.
     *
     * A MediaStore that answers with nothing is not evidence that the library is empty — it is far
     * more likely to be a permission that has just been revoked — so an empty answer prunes
     * nothing.
     */
    private suspend fun prune() {
        val existing = scanner.existingIds()
        if (existing.isEmpty()) return
        val keep = existing.map { "${SourceType.LOCAL.idPrefix}$it" }
        // Read before the delete, so the server can be told what went. Afterwards there is nothing
        // left to read the hashes from.
        val gone = trackDao.localContentHashesNotIn(keep)
        trackDao.deleteLocalTracksNotIn(keep)
        // Recorded rather than announced. The sync layer drains this when it next talks to the
        // server, which may be much later than now — the file can just as easily have gone while
        // this device was offline.
        if (gone.isNotEmpty()) {
            secureStorage.pendingForget = secureStorage.pendingForget + gone
        }
    }

    override suspend fun search(query: String): Result<List<UnifiedTrack>> =
        Result.success(
            trackDao.searchTracksInSource(SourceType.LOCAL, query).map(TrackEntity::toUnifiedTrack)
        )

    override suspend fun getStreamInfo(trackId: String): Result<StreamInfo> {
        val uri = trackDao.getTrackById(trackId)?.streamUri
            ?: return Result.failure(IOException("Local track $trackId is no longer on this device"))
        return Result.success(StreamInfo(uri = uri, format = "audio/*", isDirectFile = true))
    }

    override suspend fun getRadio(seedTrackId: String, count: Int): Result<List<UnifiedTrack>> =
        Result.success(
            trackDao.getRandomTracksInSource(SourceType.LOCAL, count).map(TrackEntity::toUnifiedTrack)
        )

    override suspend fun getRecentTracks(limit: Int): Result<List<UnifiedTrack>> =
        Result.success(
            trackDao.getRecentlyAddedInSource(SourceType.LOCAL, limit).map(TrackEntity::toUnifiedTrack)
        )

    override suspend fun getAlbums(limit: Int, offset: Int): Result<List<UnifiedAlbum>> {
        val albums = trackDao.getTracksInSource(SourceType.LOCAL)
            .groupBy { it.albumId ?: "local:album:unknown" }
            .map { (albumId, tracks) ->
                val first = tracks.first()
                UnifiedAlbum(
                    id = albumId,
                    source = SourceType.LOCAL,
                    title = first.album ?: "Unknown Album",
                    artist = first.artist,
                    coverArtUrl = first.artworkUrl,
                    songCount = tracks.size,
                    durationMs = tracks.sumOf { it.durationMs }
                )
            }
            .sortedBy { it.title }
        return Result.success(albums.drop(offset).take(limit))
    }

    override suspend fun getAlbumTracks(albumId: String): Result<List<UnifiedTrack>> =
        Result.success(trackDao.getTracksInAlbum(albumId).map(TrackEntity::toUnifiedTrack))

    override suspend fun getPlaylists(): Result<List<UnifiedPlaylist>> {
        val lists = playlistDao.getAllPlaylists().map { entity ->
            val firstTrackId = entity.trackIds.split(',').firstOrNull { it.isNotBlank() }
            val fallbackCover = if (entity.coverArtUrl.isNullOrBlank() && firstTrackId != null) {
                trackDao.getTrackById(firstTrackId)?.artworkUrl
            } else {
                entity.coverArtUrl
            }
            entity.toUnifiedPlaylist().copy(coverArtUrl = fallbackCover)
        }
        return Result.success(lists)
    }

    override suspend fun getPlaylistTracks(playlistId: String): Result<List<UnifiedTrack>> {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return Result.success(emptyList())
        val ids = entity.trackIds.split(',').filter { it.isNotBlank() }
        if (ids.isEmpty()) return Result.success(emptyList())
        val tracksById = trackDao.getTracksByIds(ids).associateBy { it.id }
        val tracks = ids.mapNotNull { id -> tracksById[id]?.toUnifiedTrack() }
        return Result.success(tracks)
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<String> {
        val id = "local:playlist:${UUID.randomUUID()}"
        val playlist = PlaylistEntity(
            id = id,
            name = name,
            trackIds = trackIds.joinToString(",")
        )
        playlistDao.insertPlaylist(playlist)
        return Result.success(id)
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> {
        val existing = playlistDao.getPlaylistById(playlistId)
            ?: return Result.failure(IOException("Playlist not found"))
        val currentIds = existing.trackIds.split(',').filter { it.isNotBlank() }
        val updatedIds = (currentIds + trackIds).distinct()
        playlistDao.updatePlaylist(
            existing.copy(
                trackIds = updatedIds.joinToString(","),
                updatedAt = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        playlistDao.deletePlaylist(playlistId)
        return Result.success(Unit)
    }
}
