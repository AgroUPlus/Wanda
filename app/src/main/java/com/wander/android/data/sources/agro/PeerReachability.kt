package com.wander.android.data.sources.agro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a peer's local address can actually be reached from here, right now.
 *
 * The route shown on a sync offer used to be inferred from the offer itself: a peer that had
 * *published* a LAN address was labelled "Direct Wi-Fi", whether or not this device could reach
 * it. That claim was wrong every time the two were on different networks — the phone on mobile
 * data, the desktop at home — and the user only found out when the transfer spent three seconds
 * timing out and quietly fell back to the relay.
 *
 * So it is measured instead of assumed. One TCP connect, with a timeout short enough that nobody
 * waits on it: a peer on the same network answers in single-digit milliseconds, and one that is
 * not there is not worth waiting longer to be told about.
 */
@Singleton
class PeerReachability @Inject constructor() {

    /**
     * True when [lanAddress] (`host:port`) accepts a connection within [TIMEOUT_MS].
     *
     * Any failure is a "no": unreachable, refused, malformed, or blocked by a permission this
     * device has not been granted. The caller cannot act differently on any of them — all of them
     * mean the transfer will not go directly.
     */
    suspend fun canReach(lanAddress: String?): Boolean {
        if (lanAddress.isNullOrBlank()) return false
        val host = lanAddress.substringBeforeLast(':').takeIf { it.isNotBlank() } ?: return false
        val port = lanAddress.substringAfterLast(':').toIntOrNull() ?: return false

        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                    true
                }
            }.getOrDefault(false)
        }
    }

    private companion object {
        /** Long enough for a device on the same Wi-Fi, short enough not to delay a screen. */
        const val TIMEOUT_MS = 600
    }
}
