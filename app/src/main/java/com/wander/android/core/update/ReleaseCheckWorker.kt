package com.wander.android.core.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wander.android.core.security.SecureStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks for a new release in the background, so one can be announced without the app being open.
 *
 * The existing launch-time check only ever fires while somebody is already using the app, which is
 * the moment they least need telling. This is the same lookup on a schedule.
 *
 * Re-checks the setting rather than trusting that the work was cancelled. A periodic request that
 * is already enqueued survives the toggle being turned off until the cancellation lands, and a
 * notification arriving after the user switched it off is exactly the thing that loses their
 * trust.
 */
@HiltWorker
class ReleaseCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateChecker: UpdateChecker,
    private val releaseNotifier: ReleaseNotifier,
    private val secureStorage: SecureStorage
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!secureStorage.isReleaseNotificationEnabled.value) return@withContext Result.success()

        when (val result = updateChecker.checkForUpdate()) {
            is UpdateCheckResult.UpdateAvailable -> {
                releaseNotifier.notifyIfNew(result)
                Result.success()
            }
            // Offline or rate-limited. Retried with the schedule's own backoff rather than
            // immediately: nothing is waiting on this, and GitHub rate-limits by address.
            UpdateCheckResult.Failed -> Result.retry()
            UpdateCheckResult.UpToDate -> Result.success()
        }
    }
}
