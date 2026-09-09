package com.wander.android.core.audio.fingerprint

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wander.android.core.notification.WorkProgressNotification
import com.wander.android.core.work.WorkControls

/**
 * The three ways the library measurement gets asked for.
 *
 * Split from [FingerprintIndexWorker] itself because they are a different concern and a different
 * audience: the worker is what runs, this is what every caller in the app touches, and keeping the
 * two in one file put the scheduling policy — constraints, uniqueness, tags — behind three hundred
 * lines about decoding.
 */
object FingerprintIndexing {

    internal const val NAME = "fingerprint-index"

    /** Names a single track to measure, instead of sweeping the library. */
    internal const val KEY_TRACK_ID = "track_id"

    /**
     * Asks for the index to be brought up to date.
     *
     * `KEEP`, so repeated calls — every launch, say — join the run already queued instead of
     * cancelling and restarting it, which on a large library would mean never finishing.
     */
    fun enqueue(context: Context, allowMobileData: Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
                .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.FINGERPRINT))
                .setConstraints(
                    Constraints.Builder()
                        // Charging is **not** required, and that is a deliberate reversal.
                        //
                        // It reads like the cautious choice and it quietly gutted the feature.
                        // A library that lives on Navidrome and YouTube Music is only reachable
                        // while the app is in use, and "in use" and "plugged in" are close to
                        // disjoint — so the index never filled, and a recogniser with an empty
                        // index is not a conservative recogniser, it is a broken one.
                        //
                        // The cost was overstated. The decode is `MediaCodec`, which is the
                        // same hardware path that plays the song, on one track at a time,
                        // bounded per run with the rest deferred to the next.
                        // What is actually expensive here is the network, and that is what the
                        // remaining two constraints are for.
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(
                            if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
                        )
                        .build()
                )
                .build()
        )
    }

    /**
     * Periodic background pass: runs when charging and unmetered Wi-Fi.
     */
    fun schedulePeriodic(context: Context) {
        val request = androidx.work.PeriodicWorkRequestBuilder<FingerprintIndexWorker>(6, java.util.concurrent.TimeUnit.HOURS)
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.FINGERPRINT))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "$NAME-periodic",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Queues one track to measure when charging and on Wi-Fi.
     */
    fun enqueueFor(context: Context, trackId: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$NAME:$trackId",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
                .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.FINGERPRINT))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .setInputData(workDataOf(KEY_TRACK_ID to trackId))
                .build()
        )
    }

    /**
     * Runs it now, ignoring every constraint, for a user who asked from Settings.
     *
     * The KDoc used to say "constraints and all" beside a builder that sets none. Asking
     * explicitly is the one case where overriding them is right — the user is looking at the
     * screen and has said to go — so the behaviour stays and the description is corrected.
     */
    fun enqueueNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
                .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.FINGERPRINT)).build()
        )
    }
}
