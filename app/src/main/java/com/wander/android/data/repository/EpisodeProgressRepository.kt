package com.wander.android.data.repository

import com.wander.android.core.database.dao.EpisodeProgressDao
import com.wander.android.core.database.entity.EpisodeProgressEntity
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.EpisodeItem
import com.wander.android.data.model.EpisodeState
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        val row = dao.get(trackId)
        if (stateOf(row) == EpisodeState.IN_PROGRESS) row?.positionMs else null
    }

    /** Every episode the listener engaged with, newest first, each with its state. */
    val episodes: Flow<List<EpisodeItem>> =
        combine(dao.observeEngagedEpisodes(), dao.observeAll()) { tracks, progress ->
            val byId = progress.associateBy(EpisodeProgressEntity::trackId)
            tracks.map { it.toEpisodeItem(byId[it.id]) }
        }.distinctUntilChanged()

    /** Episodes started and not finished, most recently listened to first. */
    val inProgress: Flow<List<UnifiedTrack>> = combine(dao.observeEngagedEpisodes(), dao.observeAll()) { tracks, progress ->
        val byId = tracks.associateBy(TrackEntity::id)
        progress
            .filter { stateOf(it) == EpisodeState.IN_PROGRESS }
            .sortedByDescending(EpisodeProgressEntity::updatedAt)
            .mapNotNull { byId[it.trackId]?.toUnifiedTrack() }
    }.distinctUntilChanged()

    /** How much of each in-progress episode has been heard, 0..1, keyed by track id. */
    val fractions: Flow<Map<String, Float>> = episodes.map { items ->
        items.filter { it.state == EpisodeState.IN_PROGRESS }.associate { it.track.id to it.fraction }
    }.distinctUntilChanged()

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

    companion object {
        /** The one definition of unplayed / in progress / finished — resume obeys it too. */
        fun stateOf(row: EpisodeProgressEntity?): EpisodeState = when {
            row == null || row.positionMs < RESUME_FLOOR_MS -> EpisodeState.UNPLAYED
            row.durationMs > 0L && row.positionMs >= row.durationMs - FINISHED_TAIL_MS -> EpisodeState.FINISHED
            else -> EpisodeState.IN_PROGRESS
        }

        private fun TrackEntity.toEpisodeItem(row: EpisodeProgressEntity?): EpisodeItem {
            val state = stateOf(row)
            val fraction = when (state) {
                EpisodeState.UNPLAYED -> 0f
                EpisodeState.FINISHED -> 1f
                // Rows written before the player's duration was remembered carry 0; the track's
                // own length, backfilled by the player, is the next-best denominator.
                EpisodeState.IN_PROGRESS -> row
                    ?.let { progress ->
                        val total = progress.durationMs.takeIf { it > 0L } ?: durationMs
                        if (total > 0L) (progress.positionMs.toFloat() / total).coerceIn(0f, 1f) else null
                    } ?: 0f
            }
            return EpisodeItem(toUnifiedTrack(), state, fraction)
        }

        private const val RESUME_FLOOR_MS = 10_000L
        private const val FINISHED_TAIL_MS = 30_000L
        private const val RETENTION_MS = 180L * 24 * 60 * 60 * 1000
    }
}
