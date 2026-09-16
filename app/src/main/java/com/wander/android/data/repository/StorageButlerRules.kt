package com.wander.android.data.repository

import java.util.concurrent.TimeUnit

/**
 * The pure half of [StorageButlerRepository]: what counts as a stale download. Kept separate from
 * the DAO/file-system reads for the same reason [TrackResolution] and [MoodRadioRanking] are — the
 * decision is testable on the JVM without a database.
 */
internal object StorageButlerRules {

    const val STALE_AFTER_DAYS = 60L
    val STALE_AFTER_MS = TimeUnit.DAYS.toMillis(STALE_AFTER_DAYS)

    /**
     * A download nobody has touched in [STALE_AFTER_DAYS] days, never liked, never played since —
     * see [StorageButlerRepository.isStale] for the caveat on what "since" means for `playCount`.
     */
    fun isStale(downloadedAt: Long?, isLiked: Boolean, playCount: Int, now: Long): Boolean {
        if (downloadedAt == null || isLiked || playCount > 0) return false
        return now - downloadedAt > STALE_AFTER_MS
    }
}
