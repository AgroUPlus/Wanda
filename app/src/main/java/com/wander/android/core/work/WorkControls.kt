package com.wander.android.core.work

import android.content.Context
import androidx.work.WorkManager
import com.wander.android.core.notification.WorkProgressNotification.Kind
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Stopping and suspending the long-running work, from one place.
 *
 * ## "Paused" is a thing this app invents
 *
 * WorkManager has no pause. A worker is running or it is not, and the only lever over one that is
 * already running is cancellation. So a pause here is two things at once: the current run is
 * cancelled, and a flag is set that every entry point consults before enqueueing another. Without
 * the flag the next periodic trigger — or the next library scan finishing — would start the work
 * straight back up, and the pause would read as having silently failed.
 *
 * That is also why the flag is persisted rather than held in memory. A pause the user set is a
 * decision, and it has to survive the process being killed, which for a background worker is the
 * normal case rather than the exception.
 *
 * ## Per operation, not one global switch
 *
 * Measuring the library, syncing it and downloading it fail differently and cost differently, and
 * the reason to stop one is rarely a reason to stop the others — pausing a thousand-track
 * measurement while leaving sync running is the common case, not an edge one. [Kind] already
 * carries the notification channel and slot for each, so it is the natural key here too.
 */
@Singleton
class WorkControls @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {

    /** Whether [kind] is suspended. Read by the UI, and by every enqueue point before scheduling. */
    fun isPaused(kind: Kind): StateFlow<Boolean> = secureStorage.workPaused(kind.name)

    /**
     * Stops [kind] now and leaves it stopped.
     *
     * The flag goes first. Cancelling releases the worker, which can let a scheduler observe the
     * work finishing and enqueue the next one — with the flag already set, that enqueue declines.
     */
    fun pause(kind: Kind) {
        secureStorage.setWorkPaused(kind.name, true)
        cancel(kind)
    }

    /** Lifts the pause. Does **not** start anything: the next ordinary trigger will. */
    fun resume(kind: Kind) = secureStorage.setWorkPaused(kind.name, false)

    /**
     * Stops the current run without suspending future ones.
     *
     * What a user means by "cancel" on a notification: stop doing this now. The work is still
     * expected to happen on its own schedule later, which is the whole difference from [pause].
     */
    fun cancel(kind: Kind) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tagFor(kind))
    }

    companion object {
        /**
         * The tag every request belonging to [kind] carries.
         *
         * A tag rather than a list of unique work names, and the fingerprint indexer is why. Its
         * sweep runs under one name, but a track you start playing is measured under
         * `fingerprint-index:<trackId>` — a name nobody can enumerate in advance. Cancelling by
         * name would have stopped the sweep and quietly left those running.
         *
         * It also removes the failure mode a name list invites: a literal here drifting from the
         * name actually enqueued, which turns cancellation into a no-op that nothing reports. A
         * request either carries the tag or it does not, and that is visible at the enqueue site.
         */
        fun tagFor(kind: Kind): String = "wanda-work-${kind.name}"
    }
}
