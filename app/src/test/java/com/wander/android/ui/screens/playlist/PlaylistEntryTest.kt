package com.wander.android.ui.screens.playlist

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaylistEntryTest {

    private fun track(id: String) = UnifiedTrack(id = id, source = SourceType.LOCAL, title = id, artist = "artist")

    @Test
    fun aSongHeldTwiceGetsTwoKeys() {
        val entries = playlistEntries(listOf(track("a"), track("b"), track("a")))

        assertEquals(listOf("a#1", "b#1", "a#2"), entries.map { it.key })
        assertEquals(listOf(0, 1, 2), entries.map { it.index })
    }

    @Test
    fun aMoveKeepsEveryKeyAndItsReadPosition() {
        val entries = playlistEntries(listOf(track("a"), track("b"), track("c")))

        val moved = entries.moving("a#1", "c#1")

        assertEquals(listOf("b#1", "c#1", "a#1"), moved.map { it.key })
        // The read position is where the edit starts from, so a drag does not rewrite it.
        assertEquals(listOf(1, 2, 0), moved.map { it.index })
    }

    @Test
    fun aFilterMatchesTitleArtistOrAlbumAndKeepsPositions() {
        val entries = playlistEntries(
            listOf(
                UnifiedTrack(id = "1", source = SourceType.LOCAL, title = "Blue Monday", artist = "New Order"),
                UnifiedTrack(id = "2", source = SourceType.LOCAL, title = "Song", artist = "Joy Division", album = "Closer"),
                UnifiedTrack(id = "3", source = SourceType.LOCAL, title = "Other", artist = "Someone")
            )
        )

        assertEquals(listOf(0), entries.filteredBy("  blue ").map { it.index })
        assertEquals(listOf(1), entries.filteredBy("DIVISION").map { it.index })
        assertEquals(listOf(1), entries.filteredBy("closer").map { it.index })
        assertSame(entries, entries.filteredBy(" "))
    }

    @Test
    fun aMoveToARowThatIsNotATrackChangesNothing() {
        val entries = playlistEntries(listOf(track("a"), track("b")))

        assertSame(entries, entries.moving("a#1", "header"))
    }
}
