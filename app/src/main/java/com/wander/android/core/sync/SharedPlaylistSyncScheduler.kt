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
 * When [SharedPlaylistSyncWorker] runs: a slow periodic pass, as the backstop for pushes missed
 * while the app was closed, and a one-off as soon as there is a connection whenever an edit made
 * here could not be sent. A pass with nothing to do is one small request.
 */
@Singleton
class SharedPlaylistSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<SharedPlaylistSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        // KEEP, so opening the app often does not keep pushing the next pass back.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Sends unsent edits once a connection is back. WorkManager keeps it across the app closing. */
    fun syncWhenOnline() {
        val request = OneTimeWorkRequestBuilder<SharedPlaylistSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PERIODIC_WORK = "wanda_shared_playlist_sync"
        const val ONE_OFF_WORK = "wanda_shared_playlist_sync_now"
    }
}
