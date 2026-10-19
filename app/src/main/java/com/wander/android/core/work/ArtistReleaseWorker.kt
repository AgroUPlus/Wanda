package com.wander.android.core.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
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
import com.wander.android.core.notification.ArtistReleaseNotifier
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.repository.ArtistSubscriptionRepository
import com.wander.android.data.sources.agro.AgroArtistRelease
import com.wander.android.data.sources.agro.AgroAuthError
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
        // Follows live on the paired server; without one there is nothing to ask, and retrying
        // would only spin the backoff until somebody pairs.
        if (!secureStorage.agroConfigured.value) return@withContext Result.success()
        // Checked before anything moves: a release read while notifications are blocked would be
        // recorded as said and never shown. Left unread, it is still there once they are allowed.
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return@withContext Result.success()
        }

        val releases = mutableListOf<AgroArtistRelease>()
        var watermark = secureStorage.lastSeenReleaseWatermark
        // Several pages per run: one page of a hundred left a first run priming only part of the
        // back catalogue, and the rest arrived six hours later as a storm of "new" releases.
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = subscriptions.newReleases(watermark, PAGE_SIZE).getOrElse { error ->
                // What was already read is still worth handling; the rest comes next run.
                if (releases.isNotEmpty()) break
                return@withContext retryOrGiveUp(error)
            }
            if (page.isEmpty()) break
            releases += page
            watermark = ReleaseTriage.nextWatermark(page, PAGE_SIZE)
            if (page.size < PAGE_SIZE) break
        }
        if (releases.isEmpty()) return@withContext Result.success()

        val primed = secureStorage.primedReleaseArtists
        val unannounced = subscriptions.notYetAnnounced(ReleaseTriage.announceable(releases, primed))

        // Everything is recorded and the watermark moved before anything is shown, deliberately: a
        // notification that fails to post is a missed notification, while state that fails to move
        // is the same batch announced again on every run, forever.
        subscriptions.recordAnnounced(ReleaseTriage.usable(releases))
        secureStorage.primedReleaseArtists = primed + releases.map(ReleaseTriage::artistKey)
        secureStorage.lastSeenReleaseWatermark = watermark

        // The catalogue sends no artwork, so the cover is whatever this device already holds by
        // that name — usually nothing for a record just out, which is why it is allowed to be null
        // rather than being waited on.
        if (unannounced.isNotEmpty()) notifier.notifyReleases(unannounced, subscriptions::artworkFor)
        Result.success()
    }

    /**
     * Retries what a later attempt could fix, a few times, and gives up on the rest.
     *
     * A rejected credential will not fix itself, and nor will a server too old to know what a
     * release query is. Giving up ends only this run: the periodic request still comes round.
     */
    private fun retryOrGiveUp(error: Throwable): Result =
        if (error is AgroAuthError || runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
        const val MAX_RETRIES = 3
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
        val request = PeriodicWorkRequestBuilder<ArtistReleaseWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints())
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

    /**
     * One check as soon as the constraints allow, for the first time the setting is turned on.
     *
     * The first run is the one that takes the followed back catalogue in silently, and leaving it to
     * the periodic request put that up to six hours away, with nothing in between to show the
     * setting had done anything at all.
     */
    fun checkSoon() {
        val request = OneTimeWorkRequestBuilder<ArtistReleaseWorker>()
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(FIRST_CHECK, ExistingWorkPolicy.KEEP, request)
    }

    fun disable() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(FIRST_CHECK)
    }

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    private companion object {
        const val PERIODIC_WORK = "artist_release_check"
        const val FIRST_CHECK = "artist_release_first_check"
    }
}
