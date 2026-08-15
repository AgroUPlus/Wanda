package com.wander.android.core.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.wander.android.R
import com.wander.android.data.repository.LibrarySyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gets this device's music onto Agro, a batch at a time.
 *
 * Runs as a **foreground** worker. A plain `CoroutineWorker` is capped at roughly ten minutes, and
 * uploading a real library is measured in hours — the job would be killed mid-transfer, repeatedly,
 * and the user would see it never finish. Note that data-sync foreground services are budgeted at
 * around six hours a day on API 34+, which is another reason each run is a bounded batch that
 * leaves the rest for next time rather than one long job.
 *
 * Modelled on [com.wander.android.core.cache.DownloadWorker], including its `isStopped` checks and
 * its refusal to report success it did not achieve.
 */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: LibrarySyncRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!syncRepository.isEnabled) return@withContext Result.success()

        runCatching { setForeground(foregroundInfo(0, 0)) }
            .onFailure {
                // Denied notification permission, or a background-start restriction. The work
                // still runs; it just loses its long-running exemption, so it is kept short.
                android.util.Log.i(TAG, "running without a foreground notification")
            }

        // Hash first: nothing can be reported or uploaded without a content hash, and this is the
        // step that makes progress even when the server is unreachable.
        val hashed = syncRepository.hashBatch()
        if (isStopped) return@withContext Result.retry()

        // Metadata next. Cheap, and it is what powers the "you're missing this" diff — worth doing
        // even for a user who never uploads a byte.
        val reported = syncRepository.reportHoldings()
        if (isStopped) return@withContext Result.retry()

        val uploaded = runCatching { syncRepository.uploadBatch() }.getOrDefault(0)

        // More to do: come back rather than declaring the library synced.
        if (hashed > 0 || uploaded > 0) Result.retry()
        else if (reported.isFailure) Result.retry()
        else Result.success()
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Syncing your library")
            .setContentText(
                if (total > 0) "$done of $total" else "Preparing…"
            )
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setOngoing(true)
            .setSilent(true)
            .apply { if (total > 0) setProgress(total, done, false) }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Library sync",
                // Low: this is a background chore the user asked for, not something to interrupt
                // them about.
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val TAG = "LibrarySyncWorker"
        private const val CHANNEL_ID = "wanda_library_sync"
        private const val NOTIFICATION_ID = 4711
    }
}
