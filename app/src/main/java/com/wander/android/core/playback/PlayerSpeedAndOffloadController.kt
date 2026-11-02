package com.wander.android.core.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import com.wander.android.core.security.SecurePlayerModePreferences
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
internal class PlayerSpeedAndOffloadController(private val store: SecurePlayerModePreferences) {

    private val _speedAndPitch = MutableStateFlow(SpeedAndPitch())
    val speedAndPitch: StateFlow<SpeedAndPitch> = _speedAndPitch.asStateFlow()

    /** Sets and remembers the speed and pitch for the kind of audio playing now. */
    fun setSpeedAndPitch(
        ctrl: MediaController?,
        requested: SpeedAndPitch,
        isEpisode: Boolean,
        route: OffloadRoute
    ) {
        if (ctrl == null) return
        val clamped = SpeedAndPitch(
            speed = requested.speed.coerceIn(SpeedAndPitch.RANGE),
            pitch = requested.pitch.coerceIn(SpeedAndPitch.RANGE)
        )
        store.setSpeedAndPitch(forEpisodes = isEpisode, value = clamped)
        apply(ctrl, clamped, route)
    }

    /**
     * Switches to the speed and pitch remembered for what is playing now — episodes keep theirs,
     * everything else shares the other — and re-decides offload for it.
     */
    fun applyRemembered(ctrl: MediaController?, isEpisode: Boolean, route: OffloadRoute) {
        if (ctrl == null) return
        apply(ctrl, store.speedAndPitch(forEpisodes = isEpisode), route)
    }

    private fun apply(ctrl: MediaController, value: SpeedAndPitch, route: OffloadRoute) {
        _speedAndPitch.value = value
        val parameters = PlaybackParameters(value.speed, value.pitch)
        // Ordered so that a rate other than 1x never reaches a player still in offload: offload
        // goes off before the rate leaves 1x, and comes back on only once the rate is back at 1x.
        if (value.isDefault) {
            if (ctrl.playbackParameters != parameters) ctrl.playbackParameters = parameters
            applyOffload(ctrl, route)
        } else {
            applyOffload(ctrl, route)
            if (ctrl.playbackParameters != parameters) ctrl.playbackParameters = parameters
        }
    }

    /** Re-decides offload for what is playing now, at the speed and pitch currently set. */
    private fun applyOffload(ctrl: MediaController, route: OffloadRoute) {
        val allowed = offloadAllowed(route.isLive, _speedAndPitch.value, route.skipsSilence, route.onBluetooth)
        ctrl.trackSelectionParameters = PlayerFactory.withVideoSuppressed(
            PlayerFactory.withOffload(ctrl.trackSelectionParameters, allowed),
            route.isLive
        )
    }
}

/** What [offloadAllowed] weighs besides the speed: the item playing, and where its sound goes. */
internal data class OffloadRoute(val isLive: Boolean, val skipsSilence: Boolean, val onBluetooth: Boolean)

/**
 * Whether audio may be offloaded to the DSP.
 *
 * Offload hands encoded audio straight to the hardware, so nothing that has to work on decoded PCM
 * can run while it is on: not the rate and pitch change of [speedAndPitch], and not the silence
 * skipping given to podcast episodes (see [EpisodeSkipSilence]), both of which are audio processors.
 * A livestream is excluded as well, because offload expects a track that ends and an HLS live
 * window is not one. Loudness normalisation does not count: it works through the player's volume
 * (see [PlayerVolume]), which offload honours.
 *
 * Nor while a Bluetooth output is connected ([BluetoothAudioOutput]): offloaded playback over
 * Bluetooth played silence on some phones until a volume key was pressed. The battery cost is
 * small there — the radio link, not the decoder, is what a Bluetooth session spends power on.
 *
 * An external equalizer attached to the session (see [ExternalEqualizer]) is not an input: the
 * platform does not tell the app whether one is attached, so there is nothing cheap to decide on.
 */
internal fun offloadAllowed(
    isLive: Boolean,
    speedAndPitch: SpeedAndPitch,
    skipsSilence: Boolean,
    onBluetooth: Boolean
): Boolean = !isLive && speedAndPitch.isDefault && !skipsSilence && !onBluetooth
