package com.wander.android.core.audio.fingerprint

import com.wander.android.R
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.work.WorkControls
import com.wander.android.data.repository.AcousticFeatureRepository
import com.wander.android.core.notification.WorkEta
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.EmbeddingRepository
import com.wander.android.data.repository.RecordingIdentityRepository
import com.wander.android.data.repository.RecordingLinkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the landmark index over the music stored on this device.
 *
 * Decoding a library is the most expensive thing the app ever does — every track is read, decoded
 * and transformed — so it runs under the project's usual background constraints: charging, and not
 * on a low battery. No network constraint, because it touches none.
 *
 * Bounded per run rather than looping to completion. A worker that decodes a thousand files in one
 * go is a worker that gets killed a few hundred in and starts again from the same place next time;
 * a bounded batch that reschedules itself always keeps the ground it gained. The next batch is a
 * fresh request appended to the same unique work, queued only while there is genuinely more to do
 * — not [Result.retry], whose exponential backoff made each batch wait longer than the last.
 *
 * Tracks that fail to decode are backed off, not retried each run. A file this device has no
 * decoder for will not acquire one, and retrying it would stall the queue behind it forever.
 */
@HiltWorker
class FingerprintIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recordingIdentity: RecordingIdentityRepository,
    private val recordingLinks: RecordingLinkRepository,
    private val secureStorage: com.wander.android.core.security.SecureStorage,
    private val acousticFeatures: AcousticFeatureRepository,
    private val embeddingSearch: EmbeddingRepository,
    decoder: PcmDecoder,
    progress: FingerprintProgress,
    private val notifications: WorkProgressNotification,
    private val workControls: WorkControls,
    musicRepository: MusicRepository,
    private val trackDao: com.wander.android.core.database.dao.TrackDao,
    private val trackAttemptDao: com.wander.android.core.database.dao.TrackAttemptDao
) : CoroutineWorker(context, params) {

    private val trackProcessor = FingerprintTrackProcessor(
        acousticFeatures = acousticFeatures,
        embeddingSearch = embeddingSearch,
        recordingIdentity = recordingIdentity,
        recordingLinks = recordingLinks,
        decoder = decoder,
        progress = progress,
        musicRepository = musicRepository,
        trackAttemptDao = trackAttemptDao
    )

    private val workerProgress = progress

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        if (workControls.isPaused(WorkProgressNotification.Kind.FINGERPRINT).value) {
            return@withContext Result.success()
        }

        // One named track when the player asked for it, the whole library otherwise.
        val requestedId = inputData.getString(FingerprintIndexing.KEY_TRACK_ID)

        // Every pending id, not a page of them: see [FingerprintBatchPlanner] for what a `LIMIT`
        // taken before the backoff filter did to a library with a few hundred failing tracks.
        val needsFeatures = acousticFeatures.needingMeasurement(NO_LIMIT)
        embeddingSearch.backfillCentroids()
        recordingIdentity.linkDuplicates(recordingLinks, secureStorage)
        val needsEmbedding = embeddingSearch.needingIndex(NO_LIMIT)

        val ordered = FingerprintBatchPlanner.eligible(
            candidates = trackAttemptDao.indexCandidates(),
            pending = needsFeatures + needsEmbedding,
            requestedId = requestedId,
            isUnreachable = workerProgress::isUnreachable,
            now = System.currentTimeMillis()
        )
        if (ordered.isEmpty()) return@withContext Result.success()

        // Full rows only for the batch itself, in the planner's order.
        val batchIds = ordered.take(BATCH_SIZE)
        val byId = trackDao.getTracksByIds(batchIds).associateBy { it.id }
        val batch = batchIds.mapNotNull { byId[it] }
        val started = System.currentTimeMillis()
        val eta = WorkEta(started)
        val foreground = promote(notifying(eta, 0, batch.size, remaining = ordered.size))

        var processed = 0
        for ((index, track) in batch.withIndex()) {
            // WorkManager discards the result of a stopped worker and reschedules it itself.
            if (isStopped) return@withContext Result.retry()
            // Stop taking new tracks before the platform's ~10-minute ceiling on a worker that is
            // not in the foreground; the rest goes to the continuation below, not to a kill.
            if (System.currentTimeMillis() - started > RUN_BUDGET_MS) break
            if (foreground) {
                promote(notifying(eta, index, batch.size, track.title, ordered.size))
            }

            trackProcessor.processTrack(
                track = track,
                needsFeatures = track.id in needsFeatures,
                needsEmbedding = track.id in needsEmbedding
            )
            processed++
        }

        // A sweep with more left hands it to a fresh request rather than `Result.retry()` — see
        // [FingerprintIndexing.enqueueContinuation]. A single-track request never continues.
        if (requestedId == null && ordered.size > processed && processed > 0) {
            FingerprintIndexing.enqueueContinuation(
                applicationContext,
                allowMobileData = inputData.getBoolean(FingerprintIndexing.KEY_ALLOW_MOBILE_DATA, false),
                explicit = inputData.getBoolean(FingerprintIndexing.KEY_EXPLICIT, false)
            )
        }
        Result.success()
    }

    /**
     * Makes this run a foreground service, which is what lifts the background time limit.
     *
     * False when the platform refuses: from Android 12 a foreground service cannot be started
     * while the app is in the background, and `setForeground` then throws
     * `ForegroundServiceStartNotAllowedException` (an [IllegalStateException]). The batch still
     * runs, bounded by [RUN_BUDGET_MS], so the refusal is logged rather than treated as failure.
     */
    private suspend fun promote(info: androidx.work.ForegroundInfo): Boolean = try {
        setForeground(info)
        true
    } catch (e: IllegalStateException) {
        android.util.Log.w(TAG, "Not promoted to foreground: ${e.javaClass.simpleName}")
        false
    }

    private fun notifying(
        eta: WorkEta,
        done: Int,
        total: Int,
        title: String? = null,
        remaining: Int = total
    ) =
        notifications.foregroundInfo(
            kind = WorkProgressNotification.Kind.FINGERPRINT,
            title = applicationContext.getString(R.string.notif_measuring_library),
            text = listOfNotNull(
                if (remaining > total) "$done of $total · $remaining left" else "$done of $total",
                eta.describe(done, total, System.currentTimeMillis()),
                title
            ).joinToString(" · "),
            done = done,
            total = total
        )

    /**
     * `internal` rather than private: [calculateBackoff] is the schedule this worker runs on, and
     * `FingerprintBackoffTest` asserts its shape — the doubling, and the 24-hour cap.
     */
    internal companion object {
        private const val TAG = "FingerprintIndex"
        private const val BATCH_SIZE = 100

        /** Wall time after which a run stops taking new tracks; under WorkManager's 10 minutes. */
        private const val RUN_BUDGET_MS = 8 * 60 * 1000L

        /** For the pending-id queries: ids are cheap, and the planner needs all of them. */
        private const val NO_LIMIT = Int.MAX_VALUE

        const val BASE_BACKOFF_MS = 5 * 60 * 1000L // 5 minutes
        const val MAX_BACKOFF_MS = 24 * 60 * 60 * 1000L // 24 hours

        fun calculateBackoff(attempts: Int): Long {
            if (attempts <= 0) return 0L
            val shift = minOf(attempts - 1, 10)
            return minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L shl shift))
        }
    }
}
