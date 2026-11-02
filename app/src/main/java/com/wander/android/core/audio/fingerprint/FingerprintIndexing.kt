package com.wander.android.core.audio.fingerprint

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
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

    /** The network rule a sweep was scheduled under, so its continuation keeps it. */
    internal const val KEY_ALLOW_MOBILE_DATA = "allow_mobile_data"

    /** Set on a sweep the user started from Settings. See [enqueueNow]. */
    internal const val KEY_EXPLICIT = "explicit"

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
            sweepRequest(allowMobileData, explicit = false)
        )
    }

    /**
     * Queues the next batch of a sweep that stopped with work still left.
     *
     * Called by the worker itself, and the reason it no longer returns `Result.retry()`: a retry
     * is WorkManager's failure path, so every batch waited an exponential backoff — 30 s, a minute,
     * two … capped at five hours — and the attempt count never reset while the sweep was going. A
     * library of a few thousand tracks spent hours waiting per hundred tracks measured. And while
     * that delayed retry sat `ENQUEUED`, every [enqueue] (each track change) joined it under `KEEP`,
     * so nothing could hurry it along.
     *
     * `APPEND_OR_REPLACE` because the caller is usually the work running under [NAME]: `KEEP` would
     * see it and drop this request, `REPLACE` would cancel the batch doing the enqueueing.
     * Appending runs it as soon as the current batch succeeds.
     */
    internal fun enqueueContinuation(context: Context, allowMobileData: Boolean, explicit: Boolean) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            sweepRequest(allowMobileData, explicit)
        )
    }

    /**
     * One batch of the library sweep.
     *
     * An [explicit] sweep — the user pressed "measure now" — sets no constraints, so its later
     * batches do not stall behind a charger or a Wi-Fi network the user already chose to go
     * without.
     */
    private fun sweepRequest(allowMobileData: Boolean, explicit: Boolean): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.FINGERPRINT))
            .setInputData(
                workDataOf(KEY_ALLOW_MOBILE_DATA to allowMobileData, KEY_EXPLICIT to explicit)
            )
        if (explicit) return builder.build()
        return builder
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
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(
                        if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
                    )
                    .build()
            )
            .build()
    }

    /**
     * Periodic background pass: runs when charging and on Wi-Fi (or mobile data if enabled).
     */
    fun schedulePeriodic(context: Context, allowMobileData: Boolean = false) {
        val request = androidx.work.PeriodicWorkRequestBuilder<FingerprintIndexWorker>(6, java.util.concurrent.TimeUnit.HOURS)
            .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.FINGERPRINT))
            // Carried so a periodic pass that hands its remainder to [enqueueContinuation] hands
            // over the same network rule.
            .setInputData(workDataOf(KEY_ALLOW_MOBILE_DATA to allowMobileData))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(
                        if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
                    )
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "$NAME-periodic",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Queues one track to measure on Wi-Fi (or mobile data if enabled).
     *
     * No charging requirement, for the same reason as [enqueue]: this is the track the user is
     * playing right now, and a phone that is playing music is rarely plugged in. One track is one
     * bounded decode, not a library sweep.
     */
    fun enqueueFor(context: Context, trackId: String, allowMobileData: Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$NAME:$trackId",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FingerprintIndexWorker>()
                .addTag(WorkControls.tagFor(WorkProgressNotification.Kind.FINGERPRINT))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(
                            if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED
                        )
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
            // Mobile data allowed: with no constraints the first batch already runs on whatever
            // network there is, and its continuations must not quietly change that.
            sweepRequest(allowMobileData = true, explicit = true)
        )
    }
}
