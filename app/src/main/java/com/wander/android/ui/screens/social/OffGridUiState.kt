package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import com.wander.android.core.p2p.NearbyPeers

/**
 * Sharing music with the phone next to you, with nothing in between.
 *
 * [isSupported] gates the whole screen and is not a failure to apologise for: Bluetooth peripheral
 * mode is missing on a real and unfixable category of Android device, and a phone that cannot
 * advertise cannot be found however long it waits.
 *
 * [isAdvertising] is deliberately not persisted anywhere. Being findable is a thing you are doing,
 * not a setting you have — see `BleDiscovery` on why a device that broadcasts continuously is a
 * device that can be followed between rooms.
 */
@Immutable
internal data class OffGridUiState(
    val isSupported: Boolean = true,
    val isAdvertising: Boolean = false,
    /** Devices in the room that said they will serve audio, nearest first. */
    val peers: List<NearbyPeers.Peer> = emptyList(),
    /** The device id of the peer this phone is linked to, or null. */
    val linkedTo: Int? = null,
    /** True while a link is being raised — which takes seconds and a system dialog. */
    val isConnecting: Boolean = false,
    val error: String? = null
) {
    /** True when scanning has found nobody yet, which is the ordinary state for the first seconds. */
    val isSearching: Boolean
        get() = peers.isEmpty() && linkedTo == null
}
