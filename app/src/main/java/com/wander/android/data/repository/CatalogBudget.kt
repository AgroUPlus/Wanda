package com.wander.android.data.repository

import com.wander.android.core.database.dao.CatalogRecordingDao

/** What the stored catalogue fingerprints may add up to before the least useful are let go. */
internal const val CATALOG_FINGERPRINT_BUDGET_BYTES = 500L * 1024 * 1024

private const val EVICTION_BATCH = 200

/**
 * Lets fingerprints go, in [CatalogRecordingDao.evictionOrder], until what is stored fits [maxBytes].
 *
 * Only the fingerprint goes: the recording keeps its title, artist and lyrics, so a lyric search
 * still finds it. What is lost is recognising it by ear, until something re-reads it.
 */
internal suspend fun CatalogRecordingDao.trimTo(maxBytes: Long = CATALOG_FINGERPRINT_BUDGET_BYTES): Int {
    var excess = vectorBytes() - maxBytes
    var dropped = 0
    while (excess > 0) {
        val batch = evictionOrder(EVICTION_BATCH)
        if (batch.isEmpty()) break
        val ids = ArrayList<String>()
        for (candidate in batch) {
            if (excess <= 0) break
            ids += candidate.recordingId
            excess -= candidate.bytes
        }
        dropFingerprints(ids)
        dropped += ids.size
    }
    return dropped
}
