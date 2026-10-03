package com.wander.android.data.repository.sharedplaylist

import com.wander.android.core.database.entity.SharedPlaylistItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPlaylistEditsTest {

    private fun item(id: String, track: String = "t-$id", by: String? = "alpha") =
        SharedPlaylistItemEntity(agroId = "pl", itemId = id, position = 0, trackId = track, title = id, artist = "A", addedBy = by)

    private fun add(pending: String, track: String) = SharedPlaylistOp.Add(pending, track, track, "A")

    private fun ids(items: List<SharedPlaylistItemEntity>) = items.map { it.itemId }

    @Test
    fun anAddAlwaysSurvivesAReplay() {
        val replayed = SharedPlaylistEdits.replay(listOf(add("pending:1", "x")), fresh = emptyList())
        assertEquals(1, replayed.kept.size)
        assertEquals(0, replayed.dropped)
    }

    @Test
    fun editsOnAnItemSomeoneElseRemovedAreDroppedAndCounted() {
        val ops = listOf(
            SharedPlaylistOp.Remove("gone"),
            SharedPlaylistOp.Move("gone", null),
            SharedPlaylistOp.Move("b", "gone"),
            SharedPlaylistOp.Remove("a"),
            SharedPlaylistOp.Move("b", "a")
        )
        val replayed = SharedPlaylistEdits.replay(ops, fresh = listOf("a", "b"))
        assertEquals(listOf(SharedPlaylistOp.Remove("a"), SharedPlaylistOp.Move("b", "a")), replayed.kept)
        assertEquals(3, replayed.dropped)
    }

    @Test
    fun localEditsApplyInOrderAndRenumber() {
        var items = listOf(item("a"), item("b"), item("c"))
        items = SharedPlaylistEdits.applyLocally(items, SharedPlaylistOp.Move("c", null), "alpha")
        items = SharedPlaylistEdits.applyLocally(items, SharedPlaylistOp.Remove("a"), "alpha")
        items = SharedPlaylistEdits.applyLocally(items, add("pending:1", "d"), "beta")

        assertEquals(listOf("c", "b", "pending:1"), ids(items))
        assertEquals(listOf(0, 1, 2), items.map { it.position })
        assertEquals("beta", items.last().addedBy)
    }

    @Test
    fun aMoveAfterAnItemLandsRightBehindIt() {
        val items = SharedPlaylistEdits.applyLocally(listOf(item("a"), item("b"), item("c")), SharedPlaylistOp.Move("a", "b"), "x")
        assertEquals(listOf("b", "a", "c"), ids(items))
    }

    @Test
    fun aMoveIsAnchoredOnTheNearestItemTheServerKnows() {
        val items = listOf(item("a"), item("pending:1"), item("b"))
        // Moving b to position 2, after a pending item the server has not created yet.
        assertEquals("a", SharedPlaylistEdits.anchorBefore(items, targetIndex = 2, moving = "b"))
        assertNull(SharedPlaylistEdits.anchorBefore(items, targetIndex = 0, moving = "b"))
    }

    @Test
    fun catchUpRemovesWhatTheLocalPlaylistLostAndAddsWhatItGained() {
        val server = listOf(item("1", "kept"), item("2", "dropped"), item("3", "dup"), item("4", "dup"))
        val local = listOf(
            KnownTrack("dup", "Dup", "A"),
            KnownTrack("kept", "Kept", "A"),
            KnownTrack("new", "New", "B")
        )
        var n = 0
        val ops = SharedPlaylistEdits.catchUpOps(server, local) { "pending:${n++}" }

        assertEquals(
            listOf(SharedPlaylistOp.Remove("2"), SharedPlaylistOp.Remove("4"), SharedPlaylistOp.Add("pending:0", "new", "New", "B")),
            ops
        )
    }

    @Test
    fun movesPutItemsInTheDesiredOrderWithTheFewestSteps() {
        val items = listOf(item("1", "a"), item("2", "b"), item("3", "c"), item("4", "d"))
        val moves = SharedPlaylistEdits.movesToMatch(items, listOf("c", "a", "b", "d"))

        assertEquals(listOf(SharedPlaylistOp.Move("3", null)), moves)
        val applied = moves.fold(items) { acc, op -> SharedPlaylistEdits.applyLocally(acc, op, "x") }
        assertEquals(listOf("c", "a", "b", "d"), applied.map { it.trackId })
    }

    @Test
    fun anAlreadyOrderedPlaylistNeedsNoMoves() {
        val items = listOf(item("1", "a"), item("2", "b"))
        assertEquals(emptyList<SharedPlaylistOp.Move>(), SharedPlaylistEdits.movesToMatch(items, listOf("a", "b")))
    }

    @Test
    fun opsSurviveTheTripThroughRoom() {
        val ops = listOf(add("pending:1", "t"), SharedPlaylistOp.Remove("r"), SharedPlaylistOp.Move("m", null))
        ops.forEach { assertEquals(it, SharedPlaylistOp.decode(SharedPlaylistOp.encode(it))) }
    }
}
