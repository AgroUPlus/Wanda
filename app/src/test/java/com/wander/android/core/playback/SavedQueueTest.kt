package com.wander.android.core.playback

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedQueueTest {

    private fun track(id: String, live: Boolean = false) =
        UnifiedTrack(id = id, source = SourceType.LOCAL, title = id, artist = "a", isLive = live)

    @Test
    fun anOrdinaryQueueIsSavedAsItStands() {
        val saved = SavedQueue.of(listOf(track("a"), track("b"), track("c")), index = 1, positionMs = 42_000L)!!
        assertEquals(listOf("a", "b", "c"), saved.tracks.map { it.id })
        assertEquals(1, saved.index)
        assertEquals(42_000L, saved.positionMs)
    }

    @Test
    fun oneShotTracksAreLeftOutAndTheIndexFollowsThePlayingTrack() {
        val saved = SavedQueue.of(
            listOf(track("relay:x"), track("a"), track("p2p:y"), track("b")),
            index = 3,
            positionMs = 5_000L
        )!!
        assertEquals(listOf("a", "b"), saved.tracks.map { it.id })
        assertEquals(1, saved.index)
        assertEquals(5_000L, saved.positionMs)
    }

    @Test
    fun aOneShotPlayingTrackResumesAtTheNextKeptTrackFromItsStart() {
        val saved = SavedQueue.of(listOf(track("a"), track("relay:x"), track("b")), index = 1, positionMs = 9_000L)!!
        assertEquals(1, saved.index)
        assertEquals("b", saved.tracks[saved.index].id)
        assertEquals(0L, saved.positionMs)
    }

    @Test
    fun aOneShotLastTrackResumesAtTheLastKeptOne() {
        val saved = SavedQueue.of(listOf(track("a"), track("relay:x")), index = 1, positionMs = 9_000L)!!
        assertEquals(0, saved.index)
    }

    @Test
    fun aLivestreamKeepsNoPosition() {
        val saved = SavedQueue.of(listOf(track("live", live = true)), index = 0, positionMs = 60_000L)!!
        assertEquals(0L, saved.positionMs)
    }

    @Test
    fun nothingSavableMeansNothingSaved() {
        assertNull(SavedQueue.of(listOf(track("relay:x")), index = 0, positionMs = 0L))
        assertNull(SavedQueue.of(emptyList(), index = 0, positionMs = 0L))
    }
}
