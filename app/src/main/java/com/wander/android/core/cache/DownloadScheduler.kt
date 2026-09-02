package com.wander.android.core.cache

import com.wander.android.core.work.WorkControls
import com.wander.android.core.notification.WorkProgressNotification
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** Wi-Fi, charging, battery not low: offline sync should cost the user nothing. */
    fun scheduleAutoDownload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<DownloadWorker>(6, TimeUnit.HOURS)
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.DOWNLOAD))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** "Download now" from Settings — the user asked, so only connectivity is required. */
    fun downloadNow() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.DOWNLOAD))
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

    internal companion object {
        const val WORK_NAME = "wanda_auto_download"
        const val IMMEDIATE_WORK = "wanda_auto_download_now"
    }
}
