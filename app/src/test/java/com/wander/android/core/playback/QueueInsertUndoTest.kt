package com.wander.android.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueInsertUndoTest {

    private val insert = QueueInsert(token = "mine", queueWasEmpty = false)

    @Test
    fun removesTheInsertedItemWhereverItHasMovedTo() {
        val tokens = listOf(null, null, null, "mine", null)

        assertEquals(InsertUndo.Remove(listOf(3)), planInsertUndo(tokens, currentIndex = 1, insert))
    }

    @Test
    fun leavesTheSameTrackQueuedByAnotherInsertAlone() {
        // Index 1 and 3 are both the same track; only the one this insert added carries its token.
        val tokens = listOf(null, "earlier", null, "mine")

        assertEquals(InsertUndo.Remove(listOf(3)), planInsertUndo(tokens, currentIndex = 0, insert))
    }

    @Test
    fun removesEveryItemOfABatchHighestIndexFirst() {
        val tokens = listOf(null, "mine", null, "mine", "mine")

        assertEquals(InsertUndo.Remove(listOf(4, 3, 1)), planInsertUndo(tokens, currentIndex = 0, insert))
    }

    @Test
    fun doesNothingOnceTheInsertIsGone() {
        val tokens = listOf(null, "other", null)

        assertEquals(InsertUndo.Unchanged, planInsertUndo(tokens, currentIndex = 0, insert))
    }

    @Test
    fun doesNothingOnAnEmptyQueue() {
        assertEquals(InsertUndo.Unchanged, planInsertUndo(emptyList(), currentIndex = -1, insert))
    }

    @Test
    fun keepsTheInsertedItemThatIsPlayingNow() {
        val tokens = listOf(null, "mine", null)

        assertEquals(InsertUndo.Unchanged, planInsertUndo(tokens, currentIndex = 1, insert))
    }

    @Test
    fun keepsOnlyThePlayingItemOfABatch() {
        val tokens = listOf(null, "mine", "mine", "mine")

        assertEquals(InsertUndo.Remove(listOf(3, 1)), planInsertUndo(tokens, currentIndex = 2, insert))
    }

    @Test
    fun clearsAQueueTheInsertFilledAndStillIsAllOf() {
        val started = QueueInsert(token = "mine", queueWasEmpty = true)

        assertEquals(InsertUndo.Clear, planInsertUndo(listOf("mine", "mine"), currentIndex = 0, started))
    }

    @Test
    fun keepsPlayingWhatAnInsertStartedOnceOtherTracksJoinedIt() {
        val started = QueueInsert(token = "mine", queueWasEmpty = true)
        val tokens = listOf("mine", null)

        assertEquals(InsertUndo.Unchanged, planInsertUndo(tokens, currentIndex = 0, started))
    }

    @Test
    fun doesNotClearAQueueThatHadOtherTracksBeforeTheInsert() {
        // Everything else was removed since; the player was not empty before this insert.
        val tokens = listOf("mine", "mine")

        assertEquals(InsertUndo.Remove(listOf(1)), planInsertUndo(tokens, currentIndex = 0, insert))
    }
}
