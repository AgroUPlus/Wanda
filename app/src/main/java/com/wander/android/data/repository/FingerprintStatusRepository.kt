package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.FingerprintProgress
import com.wander.android.core.database.dao.FingerprintDao
import com.wander.android.core.database.dao.MelodyContourDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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

    private data class Input(
        val landmarks: Set<String>,
        val contours: Set<String>,
        val indexing: String?
    )

    /**
     * Every track's status, keyed by track id, updated as the indexer works.
     *
     * ## Why a track stays blue after the indexer has let go of it
     *
     * The worker clears its marker the instant the writes return, but Room publishes a query's new
     * results asynchronously — so for a moment the track is no longer *being* measured and does not
     * yet *appear* measured. Reported naively that gap is a `MISSING`, and the badge flashed
     * blue → red → green on every single track.
     *
     * So a track that has been measured is held at [FingerprintStatus.PROCESSING] until the
     * database agrees it is done. [SETTLE_WINDOW_MS] bounds that: a track whose measurement really
     * failed stops being held and turns red, rather than sitting blue for ever on a promise nobody
     * is keeping. The ticker exists only to make that expiry happen without a new sighting — the
     * same reason `OffGridTransport` has one.
     */
    fun statuses(): Flow<Map<String, FingerprintStatus>> = flow {
        val settling = mutableMapOf<String, Long>()
        var last: Input? = null

        val inputs: Flow<Input?> = combine(
            fingerprintDao.indexedTrackIdsFlow(),
            contourDao.indexedTrackIdsFlow(MelodySearchRepository.CONTOUR_VERSION),
            progress.indexing
        ) { landmarks, contours, indexing -> Input(landmarks.toSet(), contours.toSet(), indexing) }

        val ticks: Flow<Input?> = flow {
            while (true) {
                delay(SETTLE_TICK_MS)
                emit(null)
            }
        }

        merge(inputs, ticks).collect { incoming ->
            val input = incoming ?: last ?: return@collect
            last = input

            val now = System.currentTimeMillis()
            input.indexing?.let { settling[it] = now }

            val done = input.landmarks.filterTo(mutableSetOf()) { it in input.contours }
            // Held only until the answer arrives, or until the window runs out.
            settling.keys.removeAll(done)
            settling.entries.removeAll { now - it.value > SETTLE_WINDOW_MS }

            emit(
                buildMap {
                    done.forEach { put(it, FingerprintStatus.INDEXED) }
                    // Last, so a track being re-measured reads as busy rather than as already done.
                    settling.keys.forEach { put(it, FingerprintStatus.PROCESSING) }
                }
            )
        }
    }.distinctUntilChanged()

    /** How many tracks are fully indexed, for a summary line. */
    fun indexedCount(): Flow<Int> = statuses()
        .map { statuses -> statuses.count { it.value == FingerprintStatus.INDEXED } }
        .distinctUntilChanged()

    private companion object {
        /** How long a measured track is held blue while waiting for Room to publish the write. */
        const val SETTLE_WINDOW_MS = 8_000L

        /** Fast enough that a failure turns red promptly, slow enough to be free. */
        const val SETTLE_TICK_MS = 1_000L
    }
}
