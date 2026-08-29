package com.wander.android.data.repository

import com.wander.android.core.database.dao.RecordingSplitDao
import com.wander.android.core.database.entity.RecordingSplitEntity
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's overrides of the matcher: which rows are never the same performance.
 *
 * Read on nearly every like and on every rendition lookup, and written only when someone taps
 * "not the same recording" — so the table is held in memory and re-read only after a write. The
 * cache is not an optimisation guess: [MusicRepository.renditionsOf] runs inside `toggleLike`,
 * which happens on a tap, and a table scan per heart is a real cost for a set that changes
 * approximately never.
 */
@Singleton
class RecordingSplitRepository @Inject constructor(
    private val splitDao: RecordingSplitDao
) {

    private val lock = Mutex()
    private var cached: SplitSet? = null

    /** Every pinned pair. Loaded once, then served from memory until a write invalidates it. */
    suspend fun splits(): SplitSet = lock.withLock {
        cached ?: load().also { cached = it }
    }

    /**
     * Declares [track] to be a different performance from every one of [others].
     *
     * All of them, not just one: the group the user is looking at will be folded onto a single row
     * by the migration, so pinning apart from one member alone would leave the track merged into
     * the same group by way of another. One tap has to mean what it looks like it means.
     */
    suspend fun keepApart(track: UnifiedTrack, others: List<UnifiedTrack>) {
        val rows = others
            .filter { it.id != track.id }
            .map { other ->
                val (a, b) = SplitSet.canonical(track.id, other.id)
                RecordingSplitEntity(idA = a, idB = b, pinnedAt = System.currentTimeMillis())
            }
        if (rows.isEmpty()) return
        withContext(Dispatchers.IO) { splitDao.upsert(rows) }
        invalidate()
    }

    /** Every pinned pair, for the list the user undoes them from. */
    suspend fun pinnedPairs(): List<Pair<String, String>> = splits().all.toList()

    /** Undoes one pin, letting the matcher have its say about those two rows again. */
    suspend fun rejoin(idA: String, idB: String) {
        val (a, b) = SplitSet.canonical(idA, idB)
        withContext(Dispatchers.IO) { splitDao.delete(a, b) }
        invalidate()
    }

    private suspend fun invalidate() = lock.withLock { cached = null }

    private suspend fun load(): SplitSet = withContext(Dispatchers.IO) {
        SplitSet.of(splitDao.getAllOnce().map { it.idA to it.idB })
    }
}
