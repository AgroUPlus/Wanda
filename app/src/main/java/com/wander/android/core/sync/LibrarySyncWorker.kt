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
import com.wander.android.data.repository.CatalogSyncRepository
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
internal class LibrarySyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: LibrarySyncRepository,
    private val catalogSync: CatalogSyncRepository,
    private val secureStorage: com.wander.android.core.security.SecureStorage
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
        // The notification was only ever built with (0, 0), so its bar sat at "Preparing…" for the
        // whole run however long it took. Re-posted between steps so it says something true.
        runCatching { setForeground(foregroundInfo(1, 3)) }

        // Metadata next: reports index for P2P sync across devices.
        //
        // Deletions before additions, and both here rather than only one. `reportHoldings` is
        // purely additive — the server keeps every hash it has ever been told about until it is
        // told otherwise — so a worker that reported without ever flushing the forget queue left
        // deleted files advertised from this phone until someone happened to open the app, which
        // is the one thing a background sync exists not to require.
        val forgotten = if (secureStorage.agroP2pSync) {
            syncRepository.flushPendingForget()
        } else {
            kotlin.Result.success(0)
        }
        if (isStopped) return@withContext Result.retry()

        val reported = if (secureStorage.agroP2pSync) {
            syncRepository.reportHoldings()
        } else {
            kotlin.Result.success(0)
        }
        if (isStopped) return@withContext Result.retry()

        // Last, and after reporting: the queue above only covers deletions this phone noticed and
        // managed to record. This catches the drift nothing recorded — including everything lost
        // before the queue was flushed from here at all.
        val reconciled = if (secureStorage.agroP2pSync) {
            syncRepository.reconcileHoldings()
        } else {
            kotlin.Result.success(0)
        }
        if (isStopped) return@withContext Result.retry()
        runCatching { setForeground(foregroundInfo(2, 3)) }

        // Server archiving: uploads audio bytes to Agro / Navidrome server (Admin only).
        val uploaded = if (secureStorage.agroServerArchive) {
            runCatching { syncRepository.uploadBatch() }.getOrDefault(0)
        } else {
            0
        }
        if (isStopped) return@withContext Result.retry()

        // Trade fingerprints with the catalogue, now that the metadata above is settled. Its own
        // result is deliberately ignored: with no server it answers NOT_CONFIGURED, and on a
        // failure it logs and returns rather than throwing, because a catalogue that could not be
        // reached is an optimisation missed and not a sync that failed. Nothing here may make the
        // worker retry on its account.
        catalogSync.sync()
        if (isStopped) return@withContext Result.retry()

        // Finally, say if another device has put something here worth having. The in-app card only
        // shows while the app is open, and a phone syncing on a charger overnight is exactly the
        // phone nobody is looking at.
        runCatching { notifyNewMusic() }

        // More to do: come back rather than declaring the library synced.
        if (hashed > 0 || uploaded > 0) Result.retry()
        // A failed forget is retried like a failed report. The queue survives the failure, but
        // leaving it undelivered until the next scheduled run means the fleet keeps being offered
        // files this phone has already deleted.
        else if (reported.isFailure || forgotten.isFailure || reconciled.isFailure) Result.retry()
        else Result.success()
    }

    /**
     * Tells the user when another device has music this one lacks.
     *
     * Silent when there is nothing to offer — which, with a Navidrome, is always: the repository
     * asks the server first, and a streaming setup answers "nothing" because the track is already
     * playable here. So this cannot nag a user whose library is already reachable.
     */
    private suspend fun notifyNewMusic() {
        val missing = syncRepository.missingHere().getOrNull().orEmpty()
        if (missing.isEmpty()) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureOfferChannel()

        val sampleText = missing.take(3).joinToString("\n") { "• ${it.artist} — ${it.title}" }
        val title = if (missing.size == 1) "1 track available to sync" else "${missing.size} tracks available to sync"
        val subtitle = missing.firstOrNull()?.peerSources?.firstOrNull()?.petname?.let { "From $it" } ?: "From your other devices"

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = launchIntent?.let {
            android.app.PendingIntent.getActivity(
                context,
                0,
                it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, OFFER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(subtitle)
                    .bigText(sampleText)
            )
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) setContentIntent(pendingIntent)
            }
            .build()

        runCatching { manager.notify(OFFER_NOTIFICATION_ID, notification) }
            .onFailure { android.util.Log.i(TAG, "could not post the new-music notification") }
    }

    /** Its own channel: this one is worth a glance, the sync one is a chore. */
    private fun ensureOfferChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(OFFER_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                OFFER_CHANNEL_ID,
                "New music available",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
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
        private const val OFFER_CHANNEL_ID = "wanda_new_music"
        private const val OFFER_NOTIFICATION_ID = 4712
    }
}
