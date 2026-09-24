package com.wander.android.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages reactive flows and paging for library tracks, liked tracks, recently played tracks,
 * and downloaded local tracks.
 */
internal class LibraryTrackRepository(
    private val trackDao: TrackDao,
    private val historyDao: HistoryDao,
    private val recordingRules: RecordingRulesRepository
) {
    fun getLikedTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getLikedTracksFlow().mapToTracks()

    fun getRecentlyPlayedFlow(): Flow<List<UnifiedTrack>> =
        historyDao.getRecentlyPlayedTracksFlow()
            .mapToTracks()
            .map { tracks -> recordingRules.current().distinct(tracks) }

    fun getDownloadedTracksFlow(): Flow<List<UnifiedTrack>> =
        trackDao.getDownloadedTracksFlow().mapToTracks()

    suspend fun downloadedTracks(): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getOfflineTracksOnce().map(TrackEntity::toUnifiedTrack)
    }

    fun pagedLibraryTracks(source: SourceType?): Flow<PagingData<UnifiedTrack>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = {
            if (source == null) trackDao.pagedTracks() else trackDao.pagedTracksBySource(source)
        }
    ).flow.map { page -> page.map(TrackEntity::toUnifiedTrack) }

    suspend fun tracksByIds(ids: List<String>): List<UnifiedTrack> = withContext(Dispatchers.IO) {
        trackDao.getTracksByIds(ids).map(TrackEntity::toUnifiedTrack)
    }

    suspend fun libraryTrackIds(source: SourceType?): List<String> = withContext(Dispatchers.IO) {
        if (source == null) trackDao.libraryTrackIds() else trackDao.libraryTrackIdsBySource(source)
    }

    suspend fun trackById(trackId: String): UnifiedTrack? = withContext(Dispatchers.IO) {
        trackDao.getTrackById(trackId)?.toUnifiedTrack()
    }

    suspend fun deleteDownloadedTrack(trackId: String) = withContext(Dispatchers.IO) {
        val entity = trackDao.getTrackById(trackId)
        if (entity != null) {
            entity.localFilePath?.takeIf { it.isNotBlank() }?.let { path ->
                runCatching { File(path).delete() }
            }
            trackDao.setDownloaded(trackId, isDownloaded = false, localPath = null)
        }
    }

    private fun Flow<List<TrackEntity>>.mapToTracks(): Flow<List<UnifiedTrack>> =
        map { list -> list.map(TrackEntity::toUnifiedTrack) }.flowOn(Dispatchers.Default)

    companion object {
        const val PAGE_SIZE = 60
    }
}
