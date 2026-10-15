package com.wander.android.core.playback

import com.wander.android.data.model.LoudnessMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessGainTest {

    private val both = ReplayGainInfo(trackGainDb = -6f, albumGainDb = -8f)

    @Test
    fun offLeavesTheVolumeAlone() {
        val decision = LoudnessGain.decide(both, LoudnessMode.OFF, preampDb = -6, inAlbumRun = true)

        assertTrue(decision.isOff)
        assertEquals(1f, decision.factor, 0f)
    }

    @Test
    fun trackModeUsesTheTrackGain() {
        val decision = LoudnessGain.decide(both, LoudnessMode.TRACK, preampDb = 0, inAlbumRun = true)

        assertEquals(-6f, decision.gainDb)
        assertFalse(decision.fromAlbum)
        assertEquals(0.501f, decision.factor, 0.001f)
    }

    @Test
    fun albumModeUsesTheAlbumGain() {
        val decision = LoudnessGain.decide(both, LoudnessMode.ALBUM, preampDb = 0, inAlbumRun = false)

        assertEquals(-8f, decision.gainDb)
        assertTrue(decision.fromAlbum)
    }

    @Test
    fun autoFollowsWhetherAnAlbumIsPlayingThrough() {
        assertEquals(-8f, LoudnessGain.decide(both, LoudnessMode.AUTO, 0, inAlbumRun = true).gainDb)
        assertEquals(-6f, LoudnessGain.decide(both, LoudnessMode.AUTO, 0, inAlbumRun = false).gainDb)
    }

    @Test
    fun eachModeFallsBackToTheOtherValue() {
        val albumOnly = ReplayGainInfo(trackGainDb = null, albumGainDb = -4f)

        val decision = LoudnessGain.decide(albumOnly, LoudnessMode.TRACK, preampDb = 0, inAlbumRun = false)

        assertEquals(-4f, decision.gainDb)
        assertTrue(decision.fromAlbum)
    }

    @Test
    fun aPositiveGainIsCappedAtFullVolume() {
        val quiet = ReplayGainInfo(trackGainDb = 5f, albumGainDb = null)

        assertEquals(1f, LoudnessGain.decide(quiet, LoudnessMode.TRACK, 0, false).factor, 0f)
    }

    @Test
    fun thePreampMakesRoomForAPositiveGain() {
        val quiet = ReplayGainInfo(trackGainDb = 4f, albumGainDb = null)

        val decision = LoudnessGain.decide(quiet, LoudnessMode.TRACK, preampDb = -10, inAlbumRun = false)

        assertEquals(0.501f, decision.factor, 0.001f)
    }

    @Test
    fun anUntaggedTrackGetsOnlyThePreamp() {
        val decision = LoudnessGain.decide(ReplayGainInfo.NONE, LoudnessMode.AUTO, preampDb = -6, inAlbumRun = false)

        assertNull(decision.gainDb)
        assertFalse(decision.isOff)
        assertEquals(0.501f, decision.factor, 0.001f)
    }
}
