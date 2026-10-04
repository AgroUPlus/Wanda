package com.wander.android.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When [ScrobbleForwardWorker] runs: shortly after a play, once there is a network.
 *
 * Batched the way [ScrobbleSyncScheduler.syncSoon] batches Agro: a short delay and KEEP, so an
 * album's worth of plays goes as one request a minute after the last of them rather than a request
 * per track. A run that could not reach a service retries on its own backoff, so no periodic pass
 * is needed — a pending play is always behind a request that will run.
 */
@Singleton
class ScrobbleForwardScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun forwardSoon() {
        val request = OneTimeWorkRequestBuilder<ScrobbleForwardWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK = "wanda_scrobble_forward"
    }
}
