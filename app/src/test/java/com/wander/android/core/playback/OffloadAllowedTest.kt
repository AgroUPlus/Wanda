package com.wander.android.core.playback

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffloadAllowedTest {

    private val normal = SpeedAndPitch()

    private fun allowed(
        isLive: Boolean = false,
        speedAndPitch: SpeedAndPitch = normal,
        skipsSilence: Boolean = false,
        onBluetooth: Boolean = false
    ) = offloadAllowed(isLive, speedAndPitch, skipsSilence, onBluetooth)

    @Test
    fun anOrdinaryTrackAtNormalSpeedIsOffloaded() {
        assertTrue(allowed(isLive = false, speedAndPitch = normal, skipsSilence = false))
    }

    @Test
    fun aLivestreamIsNeverOffloaded() {
        assertFalse(allowed(isLive = true, speedAndPitch = normal, skipsSilence = false))
    }

    @Test
    fun aChangedSpeedOrPitchKeepsOffloadOffForAnyTrack() {
        assertFalse(allowed(isLive = false, speedAndPitch = SpeedAndPitch(speed = 1.5f), skipsSilence = false))
        assertFalse(allowed(isLive = false, speedAndPitch = SpeedAndPitch(pitch = 0.8f), skipsSilence = false))
    }

    @Test
    fun skippingSilenceKeepsOffloadOff() {
        assertFalse(allowed(isLive = false, speedAndPitch = normal, skipsSilence = true))
    }

    @Test
    fun aConnectedBluetoothOutputKeepsOffloadOff() {
        assertFalse(allowed(onBluetooth = true))
    }

    @Test
    fun onlyEpisodesSkipSilenceAndOnlyWithTheSettingOn() {
        val episode = UnifiedTrack(id = "e", source = SourceType.LOCAL, title = "e", artist = "a", isEpisode = true)
        val song = episode.copy(isEpisode = false)

        assertTrue(skipsSilence(enabled = true, track = episode))
        assertFalse(skipsSilence(enabled = false, track = episode))
        assertFalse(skipsSilence(enabled = true, track = song))
        assertFalse(skipsSilence(enabled = true, track = null))
    }
}
