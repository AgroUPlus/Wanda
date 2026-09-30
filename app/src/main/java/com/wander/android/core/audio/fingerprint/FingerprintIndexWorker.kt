package com.wander.android.core.audio.fingerprint

import com.wander.android.R
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.work.WorkControls
import com.wander.android.data.repository.AcousticFeatureRepository
import com.wander.android.core.notification.WorkEta
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.data.repository.MusicRepository
import com.wander.android.data.repository.MelodySearchRepository
import com.wander.android.data.repository.EmbeddingRepository
import com.wander.android.data.repository.RecognitionRepository
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
 * a bounded batch that reschedules itself always keeps the ground it gained. [Result.retry] is
 * what asks for the next batch, and it only fires while there is genuinely more to do.
 *
 * Tracks that fail to decode are skipped, not retried. A file this device has no decoder for will
 * not acquire one, and retrying it would stall the queue behind it forever.
 */
@HiltWorker
class FingerprintIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recognitionRepository: RecognitionRepository,
    private val recordingIdentity: RecordingIdentityRepository,
    private val recordingLinks: RecordingLinkRepository,
    private val secureStorage: com.wander.android.core.security.SecureStorage,
    private val acousticFeatures: AcousticFeatureRepository,
    private val melodySearch: MelodySearchRepository,
    private val embeddingSearch: EmbeddingRepository,
    decoder: PcmDecoder,
    progress: FingerprintProgress,
    private val notifications: WorkProgressNotification,
    private val workControls: WorkControls,
    musicRepository: MusicRepository,
    private val trackDao: com.wander.android.core.database.dao.TrackDao,
    trackAttemptDao: com.wander.android.core.database.dao.TrackAttemptDao
) : CoroutineWorker(context, params) {

    private val trackProcessor = FingerprintTrackProcessor(
        acousticFeatures = acousticFeatures,
        melodySearch = melodySearch,
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
        val candidateIds = recognitionRepository.fingerprintableTrackIds()
            .let { all -> if (requestedId == null) all else all.filter { it == requestedId } }

        val needsFeatures = acousticFeatures.needingMeasurement(FEATURE_BATCH_LIMIT).toSet()
        val needsContour = if (com.wander.android.core.audio.melody.MelodySearch.ENABLED) {
            melodySearch.needingIndex(candidateIds).toSet()
        } else {
            emptySet()
        }
        embeddingSearch.backfillCentroids()
        recordingIdentity.linkDuplicates(recordingLinks, secureStorage)

        val needsEmbedding = embeddingSearch.needingIndex(EMBEDDING_BATCH_LIMIT)
            .let { needed -> needed.filterTo(mutableSetOf()) { it in candidateIds } }

        val now = System.currentTimeMillis()
        val pendingIds = (needsFeatures + needsContour + needsEmbedding).intersect(candidateIds.toSet())
        val candidates = if (pendingIds.isEmpty()) emptyList() else trackDao.getTracksByIds(pendingIds.toList())
        val pending = candidates.filter {
            !workerProgress.isUnreachable(it.id) && !isBackedOff(it, now)
        }
        if (pending.isEmpty()) return@withContext Result.success()

        val ordered = pending.sortedBy { track ->
            if (track.localFilePath != null || track.isDownloaded) 0 else 1
        }
        val batch = ordered.take(BATCH_SIZE)
        val eta = WorkEta(System.currentTimeMillis())
        runCatching { setForeground(notifying(eta, 0, batch.size, remaining = pending.size)) }

        for ((index, track) in batch.withIndex()) {
            if (isStopped) return@withContext Result.retry()
            runCatching { setForeground(notifying(eta, index, batch.size, track.title, pending.size)) }

            trackProcessor.processTrack(
                track = track,
                needsFeatures = track.id in needsFeatures,
                needsContour = track.id in needsContour,
                needsEmbedding = track.id in needsEmbedding
            )
        }

        if (ordered.size > BATCH_SIZE) Result.retry() else Result.success()
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
        private const val BATCH_SIZE = 100
        private const val FEATURE_BATCH_LIMIT = BATCH_SIZE * 2
        private const val EMBEDDING_BATCH_LIMIT = BATCH_SIZE * 2

        const val BASE_BACKOFF_MS = 5 * 60 * 1000L // 5 minutes
        const val MAX_BACKOFF_MS = 24 * 60 * 60 * 1000L // 24 hours

        fun calculateBackoff(attempts: Int): Long {
            if (attempts <= 0) return 0L
            val shift = minOf(attempts - 1, 10)
            return minOf(MAX_BACKOFF_MS, BASE_BACKOFF_MS * (1L shl shift))
        }
    }

    internal fun isBackedOff(track: TrackEntity, now: Long): Boolean {
        if (track.attempts <= 0) return false
        val backoffMs = calculateBackoff(track.attempts)
        val last = track.lastAttemptAt ?: return false
        return (now - last) < backoffMs
    }
}
