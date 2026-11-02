package com.wander.android.core.cache

import com.wander.android.core.work.WorkControls
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.core.security.SecureStorage
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    /**
     * Charging, battery not low, and Wi-Fi unless the user allowed mobile data: offline sync
     * should cost the user nothing they did not agree to.
     *
     * Follows the download settings: with both auto-download switches off there is no sweep at
     * all, and a changed network rule replaces the scheduled one rather than waiting behind it.
     * Called at every start and whenever one of those settings changes.
     */
    fun scheduleAutoDownload() {
        val prefs = secureStorage.downloads
        val workManager = WorkManager.getInstance(context)
        if (!prefs.autoDownloadLiked.value && !prefs.autoDownloadPredicted.value) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (prefs.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<DownloadWorker>(6, TimeUnit.HOURS)
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.DOWNLOAD))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        // UPDATE rather than KEEP: KEEP held on to whatever constraints the first schedule had, so
        // turning Wi-Fi only off would never have reached the queued sweep. UPDATE keeps its
        // place in the period, so an app start does not push the next run back.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** "Download now" from Settings — the user asked, so only connectivity is required. */
    fun downloadNow() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.DOWNLOAD))
            .setInputData(workDataOf(DownloadWorker.KEY_ON_REQUEST to true))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        // Named, where it used to be a bare `enqueue`. An unnamed request cannot be cancelled by
        // anything, so "cancel downloads" would have silently missed exactly the run the user had
        // just started by hand.
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * One podcast episode an auto-downloading show just published. Wi-Fi only and battery not low,
     * but not charging: an episode is wanted for the next commute, not the next overnight charge.
     * Unique per episode with KEEP, so a second sync pass does not queue it twice.
     */
    fun downloadEpisode(trackId: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.DOWNLOAD))
            .setInputData(workDataOf(DownloadWorker.KEY_TRACK_ID to trackId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            EPISODE_WORK_PREFIX + trackId,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Works through the tracks the user downloaded by hand — see `manualTrackIds`, which is the
     * queue. The user asked, so neither charging nor battery is required, but the Wi-Fi-only
     * setting is: a download chosen by hand is still not one the user agreed to pay data for.
     *
     * One sequential job rather than one per track, so a 200-track playlist is one download at a
     * time instead of 200 at once. APPEND_OR_REPLACE: a request made while a run is going queues
     * one more pass behind it, which picks up whatever that run read too early to see; and a chain
     * that failed (the disk filled) is replaced rather than failing everything appended to it.
     */
    fun downloadManual() = enqueueManual(ExistingWorkPolicy.APPEND_OR_REPLACE)

    /**
     * Swaps the queued manual job for one under the current Wi-Fi-only rule. A request waiting for
     * Wi-Fi would otherwise keep waiting after the user allowed mobile data, and one downloading
     * over mobile data would carry on after they took that back. Also what a resume calls, since
     * a pause cancels the job along with the rest of the downloads.
     */
    fun requeueManual() = enqueueManual(ExistingWorkPolicy.REPLACE)

    private fun enqueueManual(policy: ExistingWorkPolicy) {
        val network = if (secureStorage.downloads.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.DOWNLOAD))
            .setInputData(workDataOf(DownloadWorker.KEY_MANUAL to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK,
            policy,
            request
        )
    }

    internal companion object {
        const val MANUAL_WORK = "wanda_manual_download"
        const val WORK_NAME = "wanda_auto_download"
        const val IMMEDIATE_WORK = "wanda_auto_download_now"
        const val EPISODE_WORK_PREFIX = "wanda_episode_download_"
    }
}
