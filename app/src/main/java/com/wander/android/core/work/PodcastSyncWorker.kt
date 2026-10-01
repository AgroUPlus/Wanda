package com.wander.android.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wander.android.data.repository.PodcastRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Checks every subscribed feed for new episodes. */
@HiltWorker
internal class PodcastSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val podcasts: PodcastRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val summary = podcasts.refreshAll()
        // Retried only when nothing at all got through, which means the network is the problem.
        // A feed that keeps failing alone must not keep the whole pass in backoff.
        return if (summary.failed > 0 && summary.updated + summary.unchanged == 0) Result.retry() else Result.success()
    }
}

/** When [PodcastSyncWorker] runs. */
@Singleton
class PodcastSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Every six hours on any connection, unless the battery is low.
     *
     * Feeds are small and a 304 costs almost nothing, so metered data is not a concern here; the
     * audio itself is never fetched by this pass. KEEP, so starting the app never resets the clock.
     */
    fun enable() {
        val request = PeriodicWorkRequestBuilder<PodcastSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** A pass now, for an import or a pull-to-refresh. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<PodcastSyncWorker>().setConstraints(constraints()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    private companion object {
        const val PERIODIC_WORK = "podcast_sync"
        const val IMMEDIATE_WORK = "podcast_sync_now"
    }
}
