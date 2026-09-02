package com.wander.android.data.repository

import com.wander.android.core.audio.fingerprint.FingerprintProgress
import com.wander.android.core.database.dao.FingerprintDao
import com.wander.android.core.database.dao.MelodyContourDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn

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
// Public rather than internal only so the app-level view model can take it: the badge on the
// player's cover is read there, because the cover the user looks at is drawn outside
// `NowPlayingScreen`. Everything it exposes was already public.
class FingerprintStatusRepository @Inject constructor(
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
     * One computation of the whole library's status, shared by every screen watching it.
     *
     * ## Why this is shared rather than built per collector
     *
     * It was a cold flow, so each collector got its own copy of everything below — and there are
     * always at least two. `NowPlayingViewModel` holds one for the life of the docked player,
     * which is to say on every screen with a player on it, including the library. So the library
     * list was scrolling while, underneath it, `SELECT DISTINCT trackId FROM fingerprints` was
     * being re-run **twice** over every landmark in the library on every insert the indexer made,
     * and the map below was being rebuilt twice a second.
     *
     * Worse, it was rebuilt on the collector's dispatcher, and `stateIn(viewModelScope)` collects
     * on the main thread. A thousand-entry map, built on the UI thread, once a second, while an
     * indexing pass hammered the table it reads. That is the freeze.
     *
     * So: one flow, on [Dispatchers.Default], shared. Collectors get the last answer for free.
     */
    private val shared: Flow<Map<String, FingerprintStatus>> = flow {
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
            // A tick with nothing waiting on it has nothing to expire, and rebuilding the map to
            // discover that was the per-second cost this flow paid for the entire life of the
            // process — indexing or not, screen open or not.
            if (incoming == null && settling.isEmpty()) return@collect
            last = input

            val now = System.currentTimeMillis()
            input.indexing?.let { settling[it] = now }

            // Green means "this track can be recognised", which is the landmark fingerprint and
            // nothing else. It used to require a melody contour as well — so with humming switched
            // off every track in the library would read as unmeasured, and while humming was *on*
            // the badge was promising something the contours could not deliver anyway.
            val done = if (com.wander.android.core.audio.melody.MelodySearch.ENABLED) {
                input.landmarks.filterTo(mutableSetOf()) { it in input.contours }
            } else {
                input.landmarks.toMutableSet()
            }
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
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(
            // The repository is a `@Singleton` and outlives every screen, which is the point: the
            // player's collector and the fingerprints screen's collector now share one pass.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            // Dropped a few seconds after the last screen looks away, so a tab change does not
            // restart the queries; `replay = 1` so the screen coming back draws immediately
            // instead of flashing its loading state.
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1
        )

    /**
     * Every track's status, keyed by track id, updated as the indexer works.
     *
     * ## Why a track stays blue after the indexer has let go of it
     *
     * The worker clears its marker the instant the writes return, but Room publishes a query's new
     * results asynchronously — so for a moment the track is no longer *being* measured and does not
     * yet *appear* measured. Reported naively that gap is a `MISSING`, and the badge flashed
     * blue -> red -> green on every single track.
     *
     * So a track that has been measured is held at [FingerprintStatus.PROCESSING] until the
     * database agrees it is done. [SETTLE_WINDOW_MS] bounds that: a track whose measurement really
     * failed stops being held and turns red, rather than sitting blue for ever on a promise nobody
     * is keeping. The ticker exists only to make that expiry happen without a new sighting — the
     * same reason `OffGridTransport` has one.
     */
    fun statuses(): Flow<Map<String, FingerprintStatus>> = shared

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
