package com.wander.android.core.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages speed, pitch, and hardware offload parameters for [PlayerConnection].
 */
internal class PlayerSpeedAndOffloadController {

    private val _speedAndPitch = MutableStateFlow(SpeedAndPitch())
    val speedAndPitch: StateFlow<SpeedAndPitch> = _speedAndPitch.asStateFlow()

    fun setSpeedAndPitch(ctrl: MediaController?, speed: Float, pitch: Float, isLive: Boolean) {
        if (ctrl == null) return
        val clamped = SpeedAndPitch(
            speed = speed.coerceIn(SpeedAndPitch.RANGE),
            pitch = pitch.coerceIn(SpeedAndPitch.RANGE)
        )
        setOffloadEnabled(ctrl, clamped.isDefault, isLive)
        ctrl.playbackParameters = PlaybackParameters(clamped.speed, clamped.pitch)
        _speedAndPitch.value = clamped
    }

    fun setOffloadEnabled(ctrl: MediaController?, enabled: Boolean, isLive: Boolean) {
        if (ctrl == null) return
        val allowed = enabled && !isLive
        ctrl.trackSelectionParameters = PlayerFactory.withVideoSuppressed(
            PlayerFactory.withOffload(ctrl.trackSelectionParameters, allowed),
            isLive
        )
    }
}
