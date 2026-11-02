package com.wander.android.core.playback

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Turns audio offload off on the service's own player whenever a Bluetooth output is connected.
 *
 * The full rule, [offloadAllowed], is applied through the UI's controller — and there may be no
 * controller at all: earbuds resuming playback with the app closed start the service on its own,
 * with offload on as [PlayerFactory] built it. This covers that case. It only ever switches offload
 * off, which no other rule can object to; switching it back on is left to the controller, which
 * knows the speed and the item.
 */
internal class BluetoothOffloadVeto(player: ExoPlayer, connected: StateFlow<Boolean>, scope: CoroutineScope) {
    init {
        connected
            .filter { it }
            .onEach {
                player.trackSelectionParameters =
                    PlayerFactory.withOffload(player.trackSelectionParameters, enabled = false)
            }
            .launchIn(scope)
    }
}
