package com.wander.android.ui.screens.queue

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueEntriesTest {

    private fun track(id: String) = UnifiedTrack(id = id, source = SourceType.LOCAL, title = id, artist = "artist")

    private val queue = listOf(track("a"), track("b"), track("c"), track("d"))

    @Test
    fun rolesFollowThePlayOrderRatherThanTheIndexOrder() {
        // Shuffled: c, a, d, b — with d playing.
        val entries = queueEntries(queue, order = listOf(2, 0, 3, 1), currentIndex = 3, generations = emptyMap())

        assertEquals(listOf("c", "a", "d", "b"), entries.map { it.track.id })
        assertEquals(
            listOf(QueueItemRole.PREVIOUS, QueueItemRole.PREVIOUS, QueueItemRole.CURRENT, QueueItemRole.UP_NEXT),
            entries.map { it.role }
        )
        assertEquals(listOf(2, 0, 3, 1), entries.map { it.queueIndex })
    }

    @Test
    fun keysDoNotDependOnThePlayOrder() {
        val withRepeat = queue + track("a")
        val plain = queueEntries(withRepeat, order = listOf(0, 1, 2, 3, 4), currentIndex = 0, generations = emptyMap())
        val shuffled = queueEntries(withRepeat, order = listOf(4, 2, 0, 1, 3), currentIndex = 0, generations = emptyMap())

        assertEquals(plain.associate { it.queueIndex to it.key }, shuffled.associate { it.queueIndex to it.key })
        assertEquals(5, shuffled.map { it.key }.toSet().size)
    }

    @Test
    fun nothingPlayingMeansEverythingIsUpNext() {
        val entries = queueEntries(queue, order = listOf(0, 1, 2, 3), currentIndex = -1, generations = emptyMap())

        assertEquals(List(4) { QueueItemRole.UP_NEXT }, entries.map { it.role })
    }
}
