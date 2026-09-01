package com.wander.android.data.repository

import com.wander.android.core.database.dao.RecordingLinkDao
import com.wander.android.core.database.entity.RecordingLinkEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What the fingerprinter has decided about which rows hold the same audio.
 *
 * Cached in memory and re-read only after a write, for the same reason as
 * [RecordingSplitRepository]: this is consulted on every deduplicated list and inside every
 * rendition lookup, and it changes only when the indexing worker finishes a batch. A table scan
 * per list render would be a real cost for a set that is written a few times a day at most.
 */
@Singleton
class RecordingLinkRepository @Inject constructor(
    private val linkDao: RecordingLinkDao
) {

    private val lock = Mutex()
    private var cached: RecordingLinkSet? = null

    /** Every link found so far. Loaded once, then served from memory until a write invalidates it. */
    suspend fun links(): RecordingLinkSet = lock.withLock {
        cached ?: load().also { cached = it }
    }

    /**
     * Records that [trackId] holds the same recording as each of [matches].
     *
     * Every prior link naming [trackId] is dropped first. A re-index answers the same question
     * again with a fresher fingerprint, and a match that no longer holds — the file was replaced,
     * or the fingerprint was computed from a truncated decode — has to be able to go away. Leaving
     * stale links to accumulate would make the merge one-way, which is what
     * [RecordingSplitRepository] exists to appeal and should not have to.
     */
    suspend fun record(trackId: String, matches: List<RecordingIdentityRepository.Match>) {
        val rows = matches
            .filter { it.trackId != trackId }
            .map { match ->
                val (a, b) = SplitSet.canonical(trackId, match.trackId)
                RecordingLinkEntity(
                    idA = a,
                    idB = b,
                    similarity = match.similarity,
                    linkedAt = System.currentTimeMillis()
                )
            }
        withContext(Dispatchers.IO) {
            linkDao.clearFor(trackId)
            if (rows.isNotEmpty()) linkDao.upsert(rows)
        }
        invalidate()
    }

    /** Forgets everything known about [trackId], for a row leaving the library. */
    suspend fun forget(trackId: String) {
        withContext(Dispatchers.IO) { linkDao.clearFor(trackId) }
        invalidate()
    }

    private suspend fun invalidate() = lock.withLock { cached = null }

    private suspend fun load(): RecordingLinkSet = withContext(Dispatchers.IO) {
        RecordingLinkSet.of(linkDao.getAllOnce().map { it.idA to it.idB })
    }
}
