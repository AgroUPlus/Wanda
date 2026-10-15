package com.wander.android.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVolumeTest {

    private var written = -1f
    private val volume = PlayerVolume { written = it }

    @Test
    fun theVolumeIsTheProductOfGainAndFade() {
        volume.setNormalisation(0.5f)
        volume.setFade(0.5f)

        assertEquals(0.25f, written, 0f)
    }

    @Test
    fun aTrackChangeMidFadeKeepsTheNewTracksGainWhenTheFadeEnds() {
        volume.setNormalisation(0.5f)
        volume.setFade(0.3f)
        volume.setNormalisation(0.8f)
        volume.setFade(1f)

        assertEquals(0.8f, written, 0f)
    }
}
