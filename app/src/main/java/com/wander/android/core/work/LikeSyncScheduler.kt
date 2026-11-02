package com.wander.android.core.work

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
 * When [LikeSyncWorker] runs: as soon as there is a network, then on an exponential backoff for as
 * long as a like is still owed. KEEP, so a burst of likes made offline is one run, not one each.
 */
@Singleton
class LikeSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun syncSoon() {
        val request = OneTimeWorkRequestBuilder<LikeSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK = "wanda_like_sync"
        const val BACKOFF_SECONDS = 30L
    }
}
