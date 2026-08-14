package com.wander.android.data.sources.local

import android.content.Context
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.permissions.hasAudioPermission
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedAlbum
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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device audio, via MediaStore.
 *
 * The scan writes into Room once and every read comes back out of Room. The previous version
 * re-walked the entire MediaStore cursor on every search, stream lookup and album query.
 */
@Singleton
class LocalMusicSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scanner: MediaStoreScanner,
    private val trackDao: TrackDao,
    private val secureStorage: SecureStorage
) : IMusicSource {

    override val sourceType = SourceType.LOCAL
    override val displayName = "On this device"

    override val capabilities = SourceCapabilities(search = true, albums = true, radio = true)

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
        secureStorage.localScanWatermark = scan.watermarkSeconds
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
}
