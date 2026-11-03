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
    private val scrobbleSyncScheduler: ScrobbleSyncScheduler,
    private val scrobbleForwardScheduler: com.wander.android.core.sync.ScrobbleForwardScheduler,
    private val likeOutbox: LikeOutbox
) {
    private fun sourceFor(type: SourceType) = sources.firstOrNull { it.sourceType == type }

    fun getLikedTrackIdsFlow(): Flow<Set<String>> =
        trackDao.getLikedTrackIdsFlow().map { it.toSet() }.flowOn(Dispatchers.Default)

    /**
     * Room's like changes at once; the backend hears through [LikeOutbox], which keeps retrying
     * a like it could not send and says so once if it gives up.
     */
    suspend fun toggleLike(track: UnifiedTrack): Result<Unit> = withContext(Dispatchers.IO) {
        setLiked(track, !isLiked(track))
    }

    /** Sets rather than flips, so a gesture that only ever likes can repeat without undoing itself. */
    suspend fun setLiked(track: UnifiedTrack, liked: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        trackDao.upsertTracks(listOf(TrackEntity.fromUnifiedTrack(track)))
        trackDao.setLiked(track.id, liked)
        renditionsOf(track).forEach { trackDao.setLiked(it.id, liked) }
        val source = sourceFor(track.source)
        if (source == null || !source.capabilities.likes) return@withContext Result.success(Unit)

        likeOutbox.submit(track.id, liked)
        Result.success(Unit)
    }

    suspend fun renditionsOf(track: UnifiedTrack): List<UnifiedTrack> =
        recordingRules.current().renditionsAmong(track, candidatesFor(track.artist))

    // The artist query is a substring `LIKE`, so a blank artist would match every row and pull
    // the whole library into memory.
    private suspend fun candidatesFor(artist: String): List<UnifiedTrack> =
        if (artist.isBlank()) {
            emptyList()
        } else {
            trackDao.getTracksByArtistOnce(artist).map(TrackEntity::toUnifiedTrack)
        }

    /** Runs at every launch: one artist query per distinct artist, not one per liked track. */
    suspend fun unifySplitLikes(): Int = withContext(Dispatchers.IO) {
        val rules = recordingRules.current()
        val repaired = mutableSetOf<String>()
        trackDao.getLikedTracksOnce().map(TrackEntity::toUnifiedTrack)
            .groupBy { it.artist }
            .forEach { (artist, liked) ->
                val candidates = candidatesFor(artist)
                for (track in liked) {
                    for (other in rules.renditionsAmong(track, candidates)) {
                        if (!other.isLiked && repaired.add(other.id)) trackDao.setLiked(other.id, true)
                    }
                }
            }
        repaired.size
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
        scrobbleForwardScheduler.forwardSoon()
    }

    /**
     * Takes a play out of History. Hidden, not deleted: the row may still be waiting to reach a
     * scrobbling service, and its flags are what send it — see `HistoryEntity.hidden`.
     */
    suspend fun removeFromHistory(historyId: Long) = withContext(Dispatchers.IO) {
        historyDao.hidePlay(historyId)
        Unit
    }

    /** Clears History the same way: every play hidden, every pending scrobble still sent. */
    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao.hideAllPlays()
        Unit
    }

    private suspend fun isLiked(track: UnifiedTrack): Boolean =
        trackDao.getTrackById(track.id)?.isLiked ?: track.isLiked
}
