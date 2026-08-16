package com.wander.android.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
 * Enqueued unconditionally at startup and left alone. The worker itself checks whether Agro is
 * paired, so pairing later needs no scheduling change — the next pass simply finds a server.
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

    private companion object {
        const val PERIODIC_WORK = "wanda_scrobble_sync"
    }
}
