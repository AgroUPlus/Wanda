package com.wander.android

import com.wander.android.data.repository.StorageButlerRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** What counts as a stale download: 60 days old, unliked, unplayed. */
class StorageButlerRulesTest {

    private val now = 1_700_000_000_000L
    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    @Test
    fun `a download older than 60 days, unliked and unplayed, is stale`() {
        assertTrue(
            StorageButlerRules.isStale(downloadedAt = daysAgo(61), isLiked = false, playCount = 0, now = now)
        )
    }

    @Test
    fun `exactly at the boundary is not yet stale`() {
        assertFalse(
            StorageButlerRules.isStale(downloadedAt = daysAgo(60), isLiked = false, playCount = 0, now = now)
        )
    }

    @Test
    fun `a liked track is never stale`() {
        assertFalse(
            StorageButlerRules.isStale(downloadedAt = daysAgo(200), isLiked = true, playCount = 0, now = now)
        )
    }

    @Test
    fun `a played track is never stale`() {
        assertFalse(
            StorageButlerRules.isStale(downloadedAt = daysAgo(200), isLiked = false, playCount = 3, now = now)
        )
    }

    @Test
    fun `never downloaded is never stale`() {
        assertFalse(StorageButlerRules.isStale(downloadedAt = null, isLiked = false, playCount = 0, now = now))
    }
}
