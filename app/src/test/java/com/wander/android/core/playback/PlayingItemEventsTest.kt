package com.wander.android.core.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingItemEventsTest {

    @Test
    fun `pausing the same item does not change the playing item`() {
        assertFalse(changesPlayingItem(setOf(Player.EVENT_IS_PLAYING_CHANGED)))
    }

    @Test
    fun `switching episodes does, whichever of the three events reports it`() {
        assertTrue(changesPlayingItem(setOf(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)))
        assertTrue(changesPlayingItem(setOf(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED)))
        assertTrue(changesPlayingItem(setOf(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)))
    }
}
