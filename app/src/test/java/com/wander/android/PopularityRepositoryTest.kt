package com.wander.android

import com.wander.android.core.database.dao.PendingScrobble
import com.wander.android.data.repository.aggregatePlayCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [aggregatePlayCounts], the pure grouping step of
 * [com.wander.android.data.repository.PopularityRepository.contribute]. This is the part of the
 * "Popular on Agro" contribution path a plain unit test can reach without a live server or a real
 * `SecureStorage` — see the commit this test lands with for the grouping-key regression it guards
 * against.
 */
class PopularityRepositoryTest {

    private fun play(
        title: String,
        artist: String = "Radiohead",
        album: String? = "OK Computer",
        durationMs: Long = 383_000L,
        historyId: Long = 1L
    ) = PendingScrobble(
        historyId = historyId,
        playedAt = 0L,
        title = title,
        artist = artist,
        album = album,
        genre = null,
        durationMs = durationMs
    )

    @Test
    fun `repeated plays of the same recording are counted together`() {
        val counts = aggregatePlayCounts(
            listOf(
                play("Paranoid Android", historyId = 1),
                play("Paranoid Android", historyId = 2),
                play("Paranoid Android", historyId = 3)
            )
        )

        assertEquals(1, counts.size)
        assertEquals(3, counts.single().count)
    }

    @Test
    fun `differently tagged reports of one recording are counted together`() {
        val counts = aggregatePlayCounts(
            listOf(
                play("Paranoid Android"),
                play("Paranoid Android (Official Video) [HQ]"),
                play("paranoid android")
            )
        )

        assertEquals(1, counts.size)
        assertEquals(3, counts.single().count)
    }

    @Test
    fun `a live take is not folded into the studio cut`() {
        val counts = aggregatePlayCounts(
            listOf(
                play("Paranoid Android"),
                play("Paranoid Android (Live)")
            )
        )

        assertEquals(2, counts.size)
        assertTrue(counts.all { it.count == 1 })
    }

    @Test
    fun `entries with a blank title or artist are dropped`() {
        val counts = aggregatePlayCounts(
            listOf(
                play(title = "", artist = "Radiohead"),
                play(title = "Paranoid Android", artist = "")
            )
        )

        assertTrue(counts.isEmpty())
    }

    @Test
    fun `an empty batch produces no counts`() {
        assertTrue(aggregatePlayCounts(emptyList()).isEmpty())
    }
}
