package com.wander.android.ui.screens.social

import androidx.compose.runtime.Immutable
import com.wander.android.core.p2p.NearbyPeers
import com.wander.android.core.p2p.OffGridLink

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
    /**
     * Every live link, from either end.
     *
     * A list rather than one id, and it carries the role, because the device that was *tapped* is
     * as connected as the one that tapped — it just had no way to say so.
     */
    val links: List<OffGridLink> = emptyList(),
    /** True while a link is being raised — which takes seconds and a system dialog. */
    val isConnecting: Boolean = false,
    /** Whether this device is mirroring what the linked peer plays. */
    val isFollowing: Boolean = false,
    /** What the peer is playing, as one line, or null while nothing has been read yet. */
    val followingNowPlaying: String? = null,
    /** Set when the peer is playing something no source here can supply. */
    val followingUnresolvable: String? = null,
    val error: String? = null
) {
    /** True when scanning has found nobody yet, which is the ordinary state for the first seconds. */
    val isSearching: Boolean
        get() = peers.isEmpty() && links.isEmpty()

    /** Whether [deviceId] is one of the peers this phone is linked to, either way round. */
    fun isLinkedTo(deviceId: Int): Boolean = links.any { it.deviceId == deviceId }

    /**
     * A link to a device that is not in the scan list.
     *
     * The ordinary case for the phone that was tapped: it never scanned for the peer that reached
     * it, so the peer appears in no row, and without this the screen would show a connection the
     * user could see no evidence of.
     */
    val unlistedLinks: List<OffGridLink>
        get() = links.filterNot { link -> peers.any { it.beacon.deviceId == link.deviceId } }
}
