package com.wander.android.core.audio.fingerprint

import com.wander.android.core.database.dao.IndexCandidate

/**
 * Chooses which tracks a run of [FingerprintIndexWorker] works on, and in what order.
 *
 * Pure, so the rule can be tested without WorkManager or Room. The rule used to be split across
 * a SQL `LIMIT` and a Kotlin filter, applied in that order: the queries returned the first 200
 * tracks needing work, and only then were the ones in backoff or unreachable this session dropped.
 * Once 200 tracks were failing — a Navidrome session that expired, YouTube Music items that no
 * longer resolve — those 200 were the whole answer every time, the filter emptied it, and the
 * worker reported success with thousands of tracks still unmeasured behind them.
 */
internal object FingerprintBatchPlanner {

    /**
     * The ids worth trying now, on-device tracks first.
     *
     * [pending] is every track needing any measurement; [requestedId], when set, narrows the run
     * to that one track. Order within each group follows [candidates], so it is stable from run
     * to run.
     */
    fun eligible(
        candidates: List<IndexCandidate>,
        pending: Set<String>,
        requestedId: String?,
        isUnreachable: (String) -> Boolean,
        now: Long
    ): List<String> = candidates
        .filter { candidate ->
            candidate.id in pending &&
                (requestedId == null || candidate.id == requestedId) &&
                !isUnreachable(candidate.id) &&
                !isBackedOff(candidate.attempts, candidate.lastAttemptAt, now)
        }
        // Stable sort: decoding a file already on the device costs no network and rarely fails.
        .sortedBy { if (it.onDevice) 0 else 1 }
        .map { it.id }

    /** Whether a track that has failed [attempts] times, last at [lastAttemptAt], should wait. */
    fun isBackedOff(attempts: Int, lastAttemptAt: Long?, now: Long): Boolean {
        if (attempts <= 0) return false
        val last = lastAttemptAt ?: return false
        return (now - last) < FingerprintIndexWorker.calculateBackoff(attempts)
    }
}
