package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextShuffleOrderTest {

    private fun ShuffleOrder.order(): List<Int> {
        val out = mutableListOf<Int>()
        var index = firstIndex
        while (index != C.INDEX_UNSET) {
            out += index
            index = getNextIndex(index)
        }
        return out
    }

    private fun shuffleOf(length: Int, startIndex: Int, current: () -> Int): ShuffleOrder =
        PlayNextShuffleOrder(current, Random(42)).cloneAndSet(length, startIndex)

    @Test
    fun aNewListStartsTheShuffleAtItsStartItem() {
        val order = shuffleOf(8, startIndex = 5) { 0 }.order()

        assertEquals(5, order.first())
        assertEquals((0 until 8).toSet(), order.toSet())
    }

    @Test
    fun anInsertRightAfterThePlayingItemPlaysNextInTheShuffledOrderToo() {
        val before = shuffleOf(10, startIndex = 3) { 3 }
        val playing = before.order()

        val after = before.cloneAndInsert(/* insertionIndex = */ 4, /* insertionCount = */ 2).order()

        val at = after.indexOf(3)
        assertEquals(listOf(4, 5), after.subList(at + 1, at + 3))
        // Everything else keeps its relative order, shifted past the two new items.
        val shifted = playing.map { if (it >= 4) it + 2 else it }
        assertEquals(shifted, after.filter { it != 4 && it != 5 })
    }

    @Test
    fun anInsertAnywhereElseKeepsTheLengthAndEveryItem() {
        val before = shuffleOf(6, startIndex = 0) { 0 }

        val after = before.cloneAndInsert(/* insertionIndex = */ 6, /* insertionCount = */ 3).order()

        assertEquals((0 until 9).toSet(), after.toSet())
        assertEquals(9, after.size)
    }

    @Test
    fun aMoveKeepsEveryItemWhereItWasInTheShuffledOrder() {
        val before = shuffleOf(5, startIndex = 2) { 2 }
        val items = listOf("a", "b", "c", "d", "e")
        val playedBefore = before.order().map(items::get)

        // Item 1 moves to index 3: a, c, d, b, e.
        val movedItems = listOf("a", "c", "d", "b", "e")
        val after = before.cloneAndMove(/* indexFrom = */ 1, /* indexToExclusive = */ 2, /* newIndexFrom = */ 3)

        assertEquals(playedBefore, after.order().map(movedItems::get))
    }

    @Test
    fun reshufflingStartsAtThePlayingItem() {
        val order = shuffleOf(7, startIndex = 0) { 0 }

        assertEquals(4, (order as PlayNextShuffleOrder).reshuffledFrom(4).order().first())
    }

    @Test
    fun anEmptyOrderNeverAsksForThePlayingItem() {
        val order = PlayNextShuffleOrder({ error("read the playing item of an empty order") })

        assertEquals(3, order.cloneAndInsert(0, 3).length)
    }
}
