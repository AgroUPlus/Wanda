package com.wander.android.data.repository

import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.database.entity.HistoryEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import com.wander.android.core.sync.ScrobbleSyncScheduler
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.sources.IMusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manages like states, scrobbling, play counts, and like unification across split renditions.
 */
internal class LikesAndHistoryRepository(
    private val trackDao: TrackDao,
    private val historyDao: HistoryDao,
    private val sources: Set<IMusicSource>,
    private val recordingRules: RecordingRulesRepository,
    private val secureStorage: SecureStorage,
    private val scrobbleSuppression: ScrobbleSuppression,
    private val scrobbleSyncScheduler: ScrobbleSyncScheduler
) {
    private val _writeErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val writeErrors: SharedFlow<String> = _writeErrors.asSharedFlow()

    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    fun getLikedTrackIdsFlow(): Flow<Set<String>> =
        trackDao.getLikedTrackIdsFlow().map { it.toSet() }.flowOn(Dispatchers.Default)

    suspend fun toggleLike(track: UnifiedTrack): Result<Unit> = withContext(Dispatchers.IO) {
        val liked = !isLiked(track)
        trackDao.upsertTracks(listOf(TrackEntity.fromUnifiedTrack(track)))
        trackDao.setLiked(track.id, liked)
        renditionsOf(track).forEach { trackDao.setLiked(it.id, liked) }
        val source = sourceFor(track.source)
        if (source == null || !source.capabilities.likes) return@withContext Result.success(Unit)

        source.setLiked(track.id, liked).onFailure { cause ->
            _writeErrors.tryEmit(
                cause.message ?: "Couldn't sync that like to ${source.displayName}."
            )
        }
        Result.success(Unit)
    }

    suspend fun renditionsOf(track: UnifiedTrack): List<UnifiedTrack> {
        val candidates = trackDao.getTracksByArtistOnce(track.artist).map(TrackEntity::toUnifiedTrack)
        return recordingRules.current().renditionsAmong(track, candidates)
    }

    suspend fun unifySplitLikes(): Int = withContext(Dispatchers.IO) {
        val liked = trackDao.getLikedTracksOnce().map(TrackEntity::toUnifiedTrack)
        var repaired = 0
        for (track in liked) {
            for (other in renditionsOf(track)) {
                if (!other.isLiked) {
                    trackDao.setLiked(other.id, true)
                    repaired++
                }
            }
        }
        repaired
    }

    suspend fun recordPlay(track: UnifiedTrack) = withContext(Dispatchers.IO) {
        if (secureStorage.isIncognitoMode || scrobbleSuppression.isSuppressed) return@withContext
        if (track.isLive) return@withContext
        trackDao.incrementPlayCount(track.id, System.currentTimeMillis())
        val entryId = historyDao.recordHistory(HistoryEntity(trackId = track.id))
        val scrobbled = sourceFor(track.source)
            ?.takeIf { it.capabilities.scrobble }
            ?.scrobble(track.id)
            ?.isSuccess == true
        if (scrobbled) historyDao.markScrobbled(listOf(entryId))
        scrobbleSyncScheduler.syncSoon()
    }

    private suspend fun isLiked(track: UnifiedTrack): Boolean =
        trackDao.getTrackById(track.id)?.isLiked ?: track.isLiked
}
