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
 * When [ScrobbleSyncWorker] runs.
 *
 * Looser constraints than [LibrarySyncScheduler]: this uploads a few kilobytes of text, not a music
 * library, so requiring an unmetered connection and a charger would delay a trivial request for
 * days and leave the fleet's statistics permanently behind. Any connection will do; the battery
 * check stays, because nothing here is urgent.
 *
 * The periodic pass exists only while Agro is paired: [com.wander.android.core.work.PeriodicSyncGate]
 * schedules it on pairing and cancels it on sign-out, so an unpaired phone is not woken every two
 * hours to find there is nowhere to send anything.
 */
@Singleton
class ScrobbleSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ScrobbleSyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            // KEEP: re-registering on every launch would restart the interval each time and, on a
            // phone opened often, mean the pass never actually came due.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Stops the periodic pass. A pending [syncSoon] still runs, and finds nothing to do. */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    /**
     * Asks for a drain shortly after a play is recorded.
     *
     * The periodic pass alone meant the fleet's statistics could sit up to two hours behind
     * whatever the phone had just listened to, which is a long time to look wrong on the other
     * devices. This closes that gap without giving up the battery constraints: the work is
     * *delayed*, and [ExistingWorkPolicy.KEEP] drops a second request while one is already
     * pending — so an album's worth of plays enqueues once and uploads as a single batch a couple
     * of minutes after the last of them, rather than firing a request per track.
     *
     * WorkManager persists the request, so it still runs if the app is killed in between.
     */
    fun syncSoon() {
        val request = OneTimeWorkRequestBuilder<ScrobbleSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInitialDelay(BATCH_WINDOW_MINUTES, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val PERIODIC_WORK = "wanda_scrobble_sync"
        const val IMMEDIATE_WORK = "wanda_scrobble_sync_now"

        /** How long a burst of plays is allowed to accumulate before it is sent. */
        const val BATCH_WINDOW_MINUTES = 2L
    }
}
