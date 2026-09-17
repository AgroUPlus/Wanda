package com.wander.android.data.repository

import com.wander.android.core.database.dao.EpisodeProgressDao
import com.wander.android.core.database.entity.EpisodeProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the listener got to in each episode.
 *
 * Only two rules live here, and both exist because the naive version is worse than nothing:
 *
 * - A position within [RESUME_FLOOR_MS] of the start is not worth restoring. Resuming four
 *   seconds in reads as a glitch, not as a convenience.
 * - An episode played to within [FINISHED_TAIL_MS] of its end is finished, and a finished episode
 *   starts again from the beginning. Otherwise the last thing anyone ever hears of a podcast they
 *   completed is its final ten seconds, forever.
 */
@Singleton
class EpisodeProgressRepository @Inject constructor(
    private val dao: EpisodeProgressDao
) {

    /**
     * Where to start [trackId], or null to start at the beginning.
     */
    suspend fun resumePosition(trackId: String): Long? = withContext(Dispatchers.IO) {
        val row = dao.get(trackId) ?: return@withContext null
        when {
            row.positionMs < RESUME_FLOOR_MS -> null
            row.durationMs > 0L && row.positionMs >= row.durationMs - FINISHED_TAIL_MS -> null
            else -> row.positionMs
        }
    }

    /**
     * Records the playhead for [trackId].
     *
     * A position at the very start is recorded as a deletion rather than a zero row: it means the
     * episode was opened and not listened to, which is the same state as never having played it.
     */
    suspend fun save(trackId: String, positionMs: Long, durationMs: Long) =
        withContext(Dispatchers.IO) {
            if (positionMs < RESUME_FLOOR_MS) {
                dao.delete(trackId)
                return@withContext
            }
            dao.upsert(
                EpisodeProgressEntity(
                    trackId = trackId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

    /** Forgets positions nothing has touched in [RETENTION_MS]. */
    suspend fun prune() = withContext(Dispatchers.IO) {
        dao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)
    }

    private companion object {
        const val RESUME_FLOOR_MS = 10_000L
        const val FINISHED_TAIL_MS = 30_000L
        const val RETENTION_MS = 180L * 24 * 60 * 60 * 1000
    }
}
