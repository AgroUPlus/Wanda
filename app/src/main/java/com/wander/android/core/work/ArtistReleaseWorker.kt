package com.wander.android.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wander.android.core.notification.ArtistReleaseNotifier
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.ArtistSubscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks whether anybody the user follows has released something, and says so if they have.
 *
 * A poll rather than a push, and not for want of trying: Agro can only reach a device over
 * `/ws/sync`, which is open while the app is on screen — which is exactly when a notification is
 * least useful. Real background delivery would mean Firebase, a dependency this app does not
 * otherwise carry and which would tie a self-hosted setup to Google. So the phone asks.
 *
 * The watermark lives here rather than on the server, which is what keeps the server free of
 * per-device delivery state: it is the highest `updatedAt` this device has already been told about,
 * and the query walks forward from it.
 *
 * Re-checks the setting rather than trusting the work to have been cancelled. An enqueued periodic
 * request survives the toggle until the cancellation lands, and a notification arriving after the
 * user turned it off is the thing that loses their trust.
 */
@HiltWorker
internal class ArtistReleaseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val subscriptions: ArtistSubscriptionRepository,
    private val notifier: ArtistReleaseNotifier,
    private val secureStorage: SecureStorage
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!secureStorage.isArtistReleaseNotificationEnabled.value) {
            return@withContext Result.success()
        }

        val since = secureStorage.lastSeenReleaseWatermark
        // Retried on failure rather than treated as "nothing new": an unreachable or
        // out-of-date server must not silently advance the watermark past releases it never sent.
        val releases = subscriptions.newReleases(since).getOrElse { return@withContext Result.retry() }
        if (releases.isEmpty()) return@withContext Result.success()

        // The watermark moves before anything is shown, and deliberately: a notification that fails
        // to post is a missed notification, while a watermark that fails to move is the same batch
        // announced again on the next run, every run, forever.
        secureStorage.lastSeenReleaseWatermark = releases.maxOf { it.updatedAt }

        // Nothing to say on the very first run. Everything already in the catalogue by an artist
        // just followed would arrive at once as "new", which is a back catalogue, not news.
        if (since == 0L) return@withContext Result.success()

        notifier.notifyReleases(releases)
        Result.success()
    }
}

/** When [ArtistReleaseWorker] runs. */
@Singleton
class ArtistReleaseScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /**
     * Every six hours, on wi-fi.
     *
     * Often enough that a release is same-day news, rare enough to cost nothing. Unmetered because
     * this is a network call the user did not initiate and it should not spend their data; battery
     * rather than charging, because waiting for a charger could mean hearing about a record days
     * after it landed, which is the whole thing this exists to avoid.
     */
    fun enable() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ArtistReleaseWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            // KEEP: flipping the switch off and on again must not reset the clock, which would make
            // the toggle a way to poll on demand.
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    private companion object {
        const val PERIODIC_WORK = "artist_release_check"
    }
}
