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
}
