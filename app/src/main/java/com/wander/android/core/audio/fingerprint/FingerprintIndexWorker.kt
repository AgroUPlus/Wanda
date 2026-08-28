package com.wander.android.core.audio.fingerprint

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wander.android.data.repository.RecognitionRepository
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
    private val decoder: PcmDecoder
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val pending = recognitionRepository.tracksNeedingIndex()
        if (pending.isEmpty()) return@withContext Result.success()

        for (track in pending.take(BATCH_SIZE)) {
            if (isStopped) return@withContext Result.retry()
            val path = track.localFilePath ?: continue
            val samples = decoder.decode(path) ?: continue
            recognitionRepository.index(track, samples)
        }

        if (pending.size > BATCH_SIZE) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "fingerprint-index"

        /**
         * Tracks per run. Small enough to finish inside the ten minutes a plain worker is given,
         * with room for the slowest files in a collection.
         */
        private const val BATCH_SIZE = 25

        /**
         * Asks for the index to be brought up to date.
         *
         * `KEEP`, so repeated calls — every launch, say — join the run already queued instead of
         * cancelling and restarting it, which on a large library would mean never finishing.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresCharging(true)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
            )
        }

        /** Runs it now, constraints and all, for a user who asked from Settings. */
        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FingerprintIndexWorker>().build()
            )
        }
    }
}
