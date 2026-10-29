package com.wander.android.core.sync

import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Whether a connection reached this device over a Wi-Fi Direct group.
 *
 * The P2P server listens on every interface, so a pairing request could otherwise come from
 * anyone on the same café or office Wi-Fi. Off-grid pairing only ever runs over Wi-Fi Direct, and
 * forming that group already took the user's consent through the system's connect prompt, so the
 * interface a request arrived on is what tells a peer the user agreed to from a stranger.
 */
internal object WifiDirectInterface {

    /** Android names Wi-Fi Direct interfaces `p2p0`, `p2p-wlan0-0`, `p2p-p2p0-1` and so on. */
    private const val NAME_PREFIX = "p2p"
    private const val TAG = "WifiDirectInterface"

    fun carries(localAddress: InetAddress?): Boolean =
        localAddress != null && isWifiDirectName(interfaceName(localAddress))

    /** Null, and so refused, when the interface cannot be looked up. */
    private fun interfaceName(address: InetAddress): String? = try {
        NetworkInterface.getByInetAddress(address)?.name
    } catch (e: SocketException) {
        Log.w(TAG, "Could not look up the interface a peer reached", e)
        null
    }

    fun isWifiDirectName(name: String?): Boolean =
        name != null && name.startsWith(NAME_PREFIX, ignoreCase = true)
}
