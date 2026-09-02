package com.wander.android.core.audio.fingerprint

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which track the indexer is decoding at this moment, if any.
 *
 * In memory and nowhere else, on purpose. This is not a fact about the library — it is a fact about
 * a worker that is running right now, true for the seconds one decode takes and meaningless
 * afterwards. Writing it to Room would leave a "processing" row behind every time the process is
 * killed mid-track, and a badge that says a track is being worked on when nothing is running is
 * worse than no badge.
 *
 * A `@Singleton` rather than something the worker owns, because the worker is created per run by
 * WorkManager and the screen watching it outlives any one run.
 */
@Singleton
class FingerprintProgress @Inject constructor() {

    private val _indexing = MutableStateFlow<String?>(null)

    /** The track id being decoded, or null when nothing is. */
    val indexing: StateFlow<String?> = _indexing.asStateFlow()

    internal fun started(trackId: String) {
        _indexing.value = trackId
    }

    /**
     * Clears [trackId], and only [trackId].
     *
     * Guarded rather than unconditional so a run finishing cannot wipe the marker a newer run has
     * already set — `enqueueNow` uses `REPLACE`, so two runs can briefly overlap.
     */
    internal fun finished(trackId: String) {
        _indexing.compareAndSet(trackId, null)
    }

    /**
     * Forgets whatever was being decoded.
     *
     * Called when the work is paused or cancelled. The worker clears its own marker in a `finally`,
     * but a run stopped mid-decode can take seconds to unwind, and for those seconds every badge in
     * the app would say a paused indexer was busy — including the wave on the fingerprints screen,
     * whose whole job is to distinguish "moving" from "stopped".
     */
    fun clear() {
        _indexing.value = null
    }

    /**
     * Tracks whose audio could not be reached, so a run does not stop on them again.
     *
     * The worker's own documentation claimed failures were "skipped, not retried", and within one
     * run they were — but the candidate list is rebuilt from the database each run and a track that
     * never got measured is, by construction, still missing a measurement. So the failures floated
     * back to the head of the very next batch and the run spent its whole budget on them again.
     *
     * On a library that streams, that is not a rare case: a Navidrome track behind an expired
     * session, a YouTube Music item that no longer resolves. A hundred of those at the top of the
     * list and the sweep makes no progress at all, while its notification counts happily from zero
     * to a hundred — which is exactly what "it says it is scanning but the number never moves"
     * looks like from outside.
     *
     * Held for the process only, deliberately. This records "could not reach it just now", which is
     * usually a network or a token rather than a property of the track, and a restart is the
     * cheapest way for the user to say try again.
     */
    private val unreachable = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    internal fun couldNotReach(trackId: String) {
        unreachable.add(trackId)
    }

    internal fun isUnreachable(trackId: String): Boolean = unreachable.contains(trackId)

    /** Forgets the failures, so an explicit "measure now" really does try everything again. */
    fun retryFailures() {
        unreachable.clear()
    }
}
