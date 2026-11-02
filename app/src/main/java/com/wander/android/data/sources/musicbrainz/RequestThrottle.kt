package com.wander.android.data.sources.musicbrainz

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Spaces calls at least [intervalMs] apart, however many coroutines ask at once.
 *
 * MusicBrainz allows one request per second per client, answers a burst with 503s and may block a
 * client that keeps bursting; a few artist pages opened in quick succession are a burst. Callers
 * queue on the mutex, each waiting out what is left of the interval since the previous one began.
 */
internal class RequestThrottle(
    private val intervalMs: Long,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val mutex = Mutex()
    private var lastStartMs: Long? = null

    /** Suspends until this call may go out, and marks it as the latest. */
    suspend fun await() = mutex.withLock {
        val wait = lastStartMs?.let { it + intervalMs - nowMs() } ?: 0L
        if (wait > 0) delay(wait)
        lastStartMs = nowMs()
    }
}
