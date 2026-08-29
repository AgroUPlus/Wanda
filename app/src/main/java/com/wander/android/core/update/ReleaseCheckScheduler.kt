package com.wander.android.core.update

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

/** When [ReleaseCheckWorker] runs. */
@Singleton
class ReleaseCheckScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /**
     * Once a day, and only when nobody would mind.
     *
     * A release check is one small HTTPS request, so the constraints here are lighter than the
     * library sync's — charging is not required, because waiting for a charger could mean not
     * hearing about a release for days. Unmetered still is: this is a network call the user did
     * not initiate, and it should not spend their data.
     */
    fun enable() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            // KEEP: turning the switch off and on again should not reset the day's clock, which
            // would let a user check for updates on demand by flipping it.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    private companion object {
        const val PERIODIC_WORK = "wanda_release_check"
    }
}
