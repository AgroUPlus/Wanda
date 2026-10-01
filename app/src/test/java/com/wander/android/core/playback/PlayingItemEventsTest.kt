package com.wander.android.core.playback

import androidx.media3.common.Player
import androidx.media3.common.FlagSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingItemEventsTest {

    private fun eventsOf(vararg flags: Int) = Player.Events(FlagSet.Builder().addAll(*flags).build())

    @Test
    fun `pausing the same item does not change the playing item`() {
        assertFalse(eventsOf(Player.EVENT_IS_PLAYING_CHANGED).changesPlayingItem())
    }

    @Test
    fun `switching episodes does, whichever of the three events reports it`() {
        assertTrue(eventsOf(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION).changesPlayingItem())
        assertTrue(eventsOf(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED).changesPlayingItem())
        assertTrue(eventsOf(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY).changesPlayingItem())
    }
}
