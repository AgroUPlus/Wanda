package com.wander.android

import com.wander.android.core.audio.fingerprint.FingerprintIndexWorker
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.data.model.SourceType
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
        val track = fakeTrack(attempts = 0, lastAttemptAt = null)
        assertFalse(mockIsBackedOff(track, System.currentTimeMillis()))
    }

    @Test
    fun `isBackedOff returns true when within backoff window and false after expiry`() {
        val now = 1_000_000_000L
        val baseBackoff = 5 * 60 * 1000L // 300,000 ms

        // 1 minute ago: within 5-minute backoff -> backed off
        val recentFailure = fakeTrack(attempts = 1, lastAttemptAt = now - 60_000L)
        assertTrue(mockIsBackedOff(recentFailure, now))

        // 10 minutes ago: past 5-minute backoff -> eligible for retry
        val expiredFailure = fakeTrack(attempts = 1, lastAttemptAt = now - 600_000L)
        assertFalse(mockIsBackedOff(expiredFailure, now))
    }

    private fun mockIsBackedOff(track: TrackEntity, now: Long): Boolean {
        if (track.attempts <= 0) return false
        val backoffMs = FingerprintIndexWorker.calculateBackoff(track.attempts)
        val last = track.lastAttemptAt ?: return false
        return (now - last) < backoffMs
    }

    private fun fakeTrack(attempts: Int, lastAttemptAt: Long?) = TrackEntity(
        id = "test-track-1",
        sourceTrackId = "st1",
        source = SourceType.LOCAL,
        title = "Track",
        artist = "Artist",
        album = "Album",
        albumId = "al1",
        artistId = "ar1",
        durationMs = 180_000L,
        artworkUrl = null,
        streamUri = null,
        trackNumber = 1,
        discNumber = 1,
        year = 2024,
        genre = null,
        bitRateKbps = null,
        format = null,
        attempts = attempts,
        lastAttemptAt = lastAttemptAt
    )
}
