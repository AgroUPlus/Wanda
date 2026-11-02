package com.wander.android.ui.components.newplaylist

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainSourceForTest {

    private fun track(source: SourceType) = UnifiedTrack(id = "$source", source = source, title = "t", artist = "a")

    @Test
    fun aQueueAllFromNavidromeIsSavedThere() {
        assertEquals(SourceType.NAVIDROME, plainSourceFor(listOf(track(SourceType.NAVIDROME), track(SourceType.NAVIDROME))))
    }

    @Test
    fun aMixedQueueIsSavedAsAWandaPlaylist() {
        assertEquals(SourceType.LOCAL, plainSourceFor(listOf(track(SourceType.NAVIDROME), track(SourceType.YTMUSIC))))
        assertEquals(SourceType.LOCAL, plainSourceFor(emptyList()))
    }
}
