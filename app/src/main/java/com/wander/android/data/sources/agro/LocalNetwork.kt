package com.wander.android.data.sources.agro

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Where this device can be reached by its peers on the local network.
 *
 * Shared by [AgroClient], which reports it when the device registers, and [AgroGraphQl], which puts
 * it on the sync socket's handshake. Those two used to have no reason to agree; they do now, because
 * the server keeps the address only in memory for as long as the socket is open. A registration
 * alone stopped being enough — it happens once at app start, so a redeploy or a dropped connection
 * erased the address for good and direct transfers quietly fell back to relaying through the server
 * until the app was restarted by hand.
 */
internal object LocalNetwork {

    /** The port [com.wander.android.data.sources.agro.AgroUploader] serves peer transfers on. */
    const val P2P_PORT = 8702

    /**
     * This device's IPv4 address on the local network, as `host:port`, or null when it has none.
     *
     * IPv4 only, and deliberately: an IPv6 link-local address carries a zone id that names an
     * interface on *this* device, which means nothing to the peer being told to connect there.
     *
     * Re-read on every call rather than cached — a phone moves between networks constantly, and a
     * remembered address is wrong the moment it leaves the Wi-Fi it was found on.
     */
    fun lanAddress(): String? = localIpv4()?.let { "$it:$P2P_PORT" }

    private fun localIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { !it.isLoopback && it.isUp }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (ignored: Exception) {
            // Enumerating interfaces can throw on a device with none up. Not being reachable is a
            // normal state, not an error worth propagating to a caller who can only ignore it.
            null
        }
    }
}
