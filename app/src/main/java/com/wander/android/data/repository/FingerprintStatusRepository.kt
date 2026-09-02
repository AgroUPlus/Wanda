package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.FingerprintProgress
import com.wander.android.core.database.dao.FingerprintDao
import com.wander.android.core.database.dao.MelodyContourDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What has been measured about one track, in the terms the badge draws.
 *
 * Public where the rest of this file is internal, because `TrackRow` is public API for the ui
 * package and a public function may not name an internal type in its signature.
 */
enum class FingerprintStatus {
    /** Both the landmark fingerprint and the melody contour exist. */
    INDEXED,

    /** Being decoded at this moment. */
    PROCESSING,

    /** Neither, or only one of the two. */
    MISSING
}

/**
 * Which tracks have been fingerprinted, as one thing a screen can watch.
 *
 * Two independent indexes answer two different questions and a track can easily have one and not
 * the other: the landmark fingerprint is "is this recording that recording", the melody contour is
 * "does this go like that". A badge that claimed a track was done when only one had been written
 * would be lying on the half the user was about to use, so [FingerprintStatus.INDEXED] requires
 * both and everything short of it is [FingerprintStatus.MISSING].
 *
 * Exposed as whole sets rather than a query per track. A list draws dozens of rows a scroll, and a
 * lookup each would be dozens of round trips a frame; these are two queries the whole screen shares.
 */
@Singleton
internal class FingerprintStatusRepository @Inject constructor(
    private val fingerprintDao: FingerprintDao,
    private val contourDao: MelodyContourDao,
    private val progress: FingerprintProgress
) {

    /** Every track's status, keyed by track id, updated as the indexer works. */
    fun statuses(): Flow<Map<String, FingerprintStatus>> = combine(
        fingerprintDao.indexedTrackIdsFlow(),
        contourDao.indexedTrackIdsFlow(MelodySearchRepository.CONTOUR_VERSION),
        progress.indexing
    ) { landmarks, contours, indexing ->
        val withContour = contours.toSet()
        val done = landmarks.filterTo(mutableSetOf()) { it in withContour }

        buildMap {
            done.forEach { put(it, FingerprintStatus.INDEXED) }
            // Last, so a track being re-measured reads as busy rather than as already done.
            indexing?.let { put(it, FingerprintStatus.PROCESSING) }
        }
    }.distinctUntilChanged()

    /** How many tracks are fully indexed, for a summary line. */
    fun indexedCount(): Flow<Int> = statuses()
        .map { statuses -> statuses.count { it.value == FingerprintStatus.INDEXED } }
        .distinctUntilChanged()
}
