package com.wander.android.core.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages speed, pitch, and hardware offload parameters for [PlayerConnection].
 *
 * Offload is decided here and nowhere else — see [offloadAllowed]. It used to be decided in two
 * places that each knew half the rule: a speed change turned it off, and the next livestream check
 * turned it straight back on at whatever speed was set.
 */
internal class PlayerSpeedAndOffloadController {

    private val _speedAndPitch = MutableStateFlow(SpeedAndPitch())
    val speedAndPitch: StateFlow<SpeedAndPitch> = _speedAndPitch.asStateFlow()

    fun setSpeedAndPitch(ctrl: MediaController?, speed: Float, pitch: Float, isLive: Boolean, skipsSilence: Boolean) {
        if (ctrl == null) return
        val clamped = SpeedAndPitch(
            speed = speed.coerceIn(SpeedAndPitch.RANGE),
            pitch = pitch.coerceIn(SpeedAndPitch.RANGE)
        )
        _speedAndPitch.value = clamped
        // Before the new rate: a rate other than 1x must never reach a player still in offload.
        applyOffload(ctrl, isLive, skipsSilence)
        ctrl.playbackParameters = PlaybackParameters(clamped.speed, clamped.pitch)
    }

    /** Re-decides offload for what is playing now, at the speed and pitch currently set. */
    fun applyOffload(ctrl: MediaController?, isLive: Boolean, skipsSilence: Boolean) {
        if (ctrl == null) return
        val allowed = offloadAllowed(isLive, _speedAndPitch.value, skipsSilence)
        ctrl.trackSelectionParameters = PlayerFactory.withVideoSuppressed(
            PlayerFactory.withOffload(ctrl.trackSelectionParameters, allowed),
            isLive
        )
    }
}

/**
 * Whether audio may be offloaded to the DSP.
 *
 * Offload hands encoded audio straight to the hardware, so nothing that has to work on decoded PCM
 * can run while it is on: not the rate and pitch change of [speedAndPitch], and not the silence
 * skipping given to podcast episodes (see [EpisodeSkipSilence]), both of which are audio processors.
 * A livestream is excluded as well, because offload expects a track that ends and an HLS live
 * window is not one. Loudness normalisation does not count: it works through the player's volume
 * (see [PlayerVolume]), which offload honours.
 */
internal fun offloadAllowed(isLive: Boolean, speedAndPitch: SpeedAndPitch, skipsSilence: Boolean): Boolean =
    !isLive && speedAndPitch.isDefault && !skipsSilence
