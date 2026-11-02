package com.wander.android

import com.wander.android.core.audio.fingerprint.FingerprintBatchPlanner
import com.wander.android.core.audio.fingerprint.FingerprintIndexWorker
import com.wander.android.core.database.dao.IndexCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintBackoffTest {

    @Test
    fun `calculateBackoff scales exponentially and caps at 24 hours`() {
        assertEquals(0L, FingerprintIndexWorker.calculateBackoff(0))
        // Attempt 1: base = 5 min
        assertEquals(5 * 60 * 1000L, FingerprintIndexWorker.calculateBackoff(1))
        // Attempt 2: 10 min
        assertEquals(10 * 60 * 1000L, FingerprintIndexWorker.calculateBackoff(2))
        // Attempt 3: 20 min
        assertEquals(20 * 60 * 1000L, FingerprintIndexWorker.calculateBackoff(3))
        // Attempt 4: 40 min
        assertEquals(40 * 60 * 1000L, FingerprintIndexWorker.calculateBackoff(4))
        // Huge attempt count caps at 24 hours
        assertEquals(24 * 60 * 60 * 1000L, FingerprintIndexWorker.calculateBackoff(20))
    }

    @Test
    fun `isBackedOff returns false for zero attempts`() {
        assertFalse(FingerprintBatchPlanner.isBackedOff(0, null, System.currentTimeMillis()))
    }

    @Test
    fun `isBackedOff returns true when within backoff window and false after expiry`() {
        val now = 1_000_000_000L
        // 1 minute ago: within the 5-minute backoff
        assertTrue(FingerprintBatchPlanner.isBackedOff(1, now - 60_000L, now))
        // 10 minutes ago: past it, eligible again
        assertFalse(FingerprintBatchPlanner.isBackedOff(1, now - 600_000L, now))
    }

    @Test
    fun `backed-off tracks do not hide the tracks behind them`() {
        val now = 1_000_000_000L
        // The regression: 300 failing tracks ahead of the healthy one used to fill a LIMIT 200
        // page, the backoff filter emptied it, and the run ended with work left.
        val failing = (0 until 300).map { candidate("fail$it", attempts = 3, lastAttemptAt = now) }
        val healthy = candidate("ok")
        val ids = FingerprintBatchPlanner.eligible(
            candidates = failing + healthy,
            pending = (failing + healthy).map { it.id }.toSet(),
            requestedId = null,
            isUnreachable = { false },
            now = now
        )
        assertEquals(listOf("ok"), ids)
    }

    @Test
    fun `eligible puts on-device tracks first and keeps order otherwise`() {
        val ids = FingerprintBatchPlanner.eligible(
            candidates = listOf(
                candidate("remote1"),
                candidate("local1", onDevice = true),
                candidate("remote2"),
                candidate("done"),
                candidate("unreachable", onDevice = true)
            ),
            pending = setOf("remote1", "local1", "remote2", "unreachable"),
            requestedId = null,
            isUnreachable = { it == "unreachable" },
            now = 0L
        )
        assertEquals(listOf("local1", "remote1", "remote2"), ids)
    }

    @Test
    fun `a requested track narrows the run to that track`() {
        val ids = FingerprintBatchPlanner.eligible(
            candidates = listOf(candidate("a"), candidate("b")),
            pending = setOf("a", "b"),
            requestedId = "b",
            isUnreachable = { false },
            now = 0L
        )
        assertEquals(listOf("b"), ids)
    }

    private fun candidate(
        id: String,
        attempts: Int = 0,
        lastAttemptAt: Long? = null,
        onDevice: Boolean = false
    ) = IndexCandidate(id, attempts, lastAttemptAt, onDevice)
}
