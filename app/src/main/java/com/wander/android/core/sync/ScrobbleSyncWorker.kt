package com.wander.android.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroStatsApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Drains the play outbox to Agro, so the fleet's statistics include this device.
 *
 * The `history` table was already built as an outbox — `agroSynced = false` until the server has
 * it — and this is what empties it. Deliberately *not* a foreground worker, unlike
 * [LibrarySyncWorker]: a batch of a few hundred rows of text is one small request, nothing like an
 * audio upload, and it finishes well inside the ordinary ten-minute budget.
 *
 * Nothing here is retried by hand. A failed batch simply stays unsynced and the next run picks it
 * up, which is also what happens after a reinstall of the server or a week with no connection.
 */
@HiltWorker
class ScrobbleSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val historyDao: HistoryDao,
    private val statsApi: AgroStatsApi,
    private val popularityRepository: com.wander.android.data.repository.PopularityRepository,
    private val secureStorage: SecureStorage
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Nothing to report to. Success rather than failure: there is no error here, just no
        // server, and a retrying worker would spin for as long as the app stays unpaired.
        if (!secureStorage.agroConfigured.value) return@withContext Result.success()

        val deviceName = secureStorage.agroDevicePetname.ifBlank { secureStorage.agroDeviceId }

        // Bounded, and looped rather than fetched in one go: a phone that has been offline for a
        // fortnight has more plays than belong in a single request, and the server caps the batch
        // size anyway.
        repeat(MAX_BATCHES_PER_RUN) {
            if (isStopped) return@withContext Result.success()

            val pending = historyDao.getPendingAgroScrobbles(BATCH_SIZE)
            if (pending.isEmpty()) return@withContext Result.success()

            val sent = statsApi.recordScrobbles(deviceName, pending)
            if (sent.isFailure) {
                // Retried on the worker's own backoff. The rows stay pending, so nothing is lost
                // and nothing is double-counted — the server ignores a play it already has.
                return@withContext Result.retry()
            }
            historyDao.markAgroSynced(pending.map { it.historyId })

            // After the history is safely reported and marked, never before. Counts are the
            // losable half: they carry no submitter identity, so a failed contribution cannot be
            // retried without inflating the total it is reporting, and it is dropped instead.
            // Doing it first would risk the user's own history for a shelf.
            popularityRepository.contribute(pending)
        }

        Result.success()
    }

    private companion object {
        /** Comfortably under the server's own per-request cap. */
        const val BATCH_SIZE = 200

        /** Ceiling on one run, so a very stale outbox is drained over several rather than at once. */
        const val MAX_BATCHES_PER_RUN = 10
    }
}
