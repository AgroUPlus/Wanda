package com.wander.android.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When [LibrarySyncWorker] runs.
 *
 * Two entry points, the same split as [com.wander.android.core.cache.DownloadScheduler]: an
 * automatic pass under strict constraints, and a user-initiated one that runs as soon as there is
 * a connection because the user is standing there having just asked for it.
 */
@Singleton
class LibrarySyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * The background pass: unmetered, charging, not low on battery.
     *
     * Uploading a music library is the single most data- and battery-hungry thing this app can do,
     * so unattended it happens only in the circumstances where nobody would mind — which is the
     * same rule the download worker follows.
     */
    fun enablePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun disablePeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    /** "Sync now". Only needs a connection — the user is asking for it deliberately. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<LibrarySyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            // REPLACE, not KEEP: tapping again should restart rather than silently do nothing.
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private companion object {
        const val PERIODIC_WORK = "wanda_library_sync"
        const val IMMEDIATE_WORK = "wanda_library_sync_now"
    }
}
