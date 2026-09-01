package com.wander.android.core.p2p

import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.sync.P2PServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The off-grid tier, as one thing the rest of the app can ask for.
 *
 * Two radios doing two jobs: BLE finds who is there, cheaply and continuously; Wi-Fi Direct carries
 * the audio, expensively and only once somebody has been chosen. Splitting them that way is what
 * makes the feature affordable — discovery over Wi-Fi Direct would mean holding a group open to be
 * found, and a phone cannot do that all afternoon.
 *
 * When a link exists, the peer is reachable at an ordinary address and the HTTP server that already
 * serves the LAN tier serves this one too. Nothing below this class knows which radio carried the
 * bytes.
 *
 * ## Deliberately manual
 *
 * Nothing here starts on its own. Advertising is a broadcast to a room and connecting is a radio
 * held open, and neither is something to do speculatively in the background on the chance it is
 * useful — that is a battery cost and a trackability cost paid continuously for an occasional
 * benefit. The user asks; this obliges; it stops when they stop.
 */
@Singleton
internal class OffGridTransport @Inject constructor(
    private val ble: BleDiscovery,
    private val wifiDirect: WifiDirectLink,
    private val identityKeys: IdentityKeyManager,
    private val p2pServer: P2PServer
) {

    private val peers = NearbyPeers()
    private val linkMutex = Mutex()
    private var link: DirectLink? = null

    /** Whether this device can do any of it. False on a phone without BLE peripheral support. */
    val isSupported: Boolean
        get() = ble.isAvailable && wifiDirect.isAvailable

    /**
     * Starts telling the room this device is here.
     *
     * [servesAudio] is a promise about what a peer will find if it connects, so it is set from
     * whether the server is actually running rather than from an intention to run it.
     */
    fun startAdvertising(servesAudio: Boolean): Boolean {
        val publicKey = identityKeys.getOrCreateIdentityKeys().second.encoded
        return ble.advertise(
            OffGridBeacon(
                deviceId = OffGridBeacon.deviceIdFrom(publicKey),
                fingerprint = OffGridBeacon.fingerprintFrom(publicKey),
                servesAudio = servesAudio
            )
        )
    }

    fun stopAdvertising() = ble.stopAdvertising()

    /**
     * Devices in the room that will serve audio, nearest first, updated as they are heard.
     *
     * The list is rebuilt on every sighting rather than emitted per beacon, because a UI wants "who
     * is here now" and a beacon stream is not that — see [NearbyPeers] for the three decisions in
     * between.
     */
    fun nearbyServers(): Flow<List<NearbyPeers.Peer>> = ble.scan().map { (beacon, rssi) ->
        val now = System.currentTimeMillis()
        peers.sighted(beacon, rssi, now)
        peers.servers(now)
    }

    /**
     * Raises a direct link and returns the base URL the peer's library is reachable at.
     *
     * Null when no link could be formed, which is ordinary: see [WifiDirectLink] on how many ways
     * group formation fails without anything being wrong.
     */
    suspend fun connect(): String? = linkMutex.withLock {
        link?.let { return@withLock baseUrlOf(it) }
        val formed = wifiDirect.connect() ?: return@withLock null
        link = formed
        baseUrlOf(formed)
    }

    /**
     * The base URL of a link that is *already* up, or null.
     *
     * Non-suspending and side-effect free, unlike [connect]. The resolver runs while a listener is
     * waiting for the next track to start, so it may ask whether a link exists but must never be
     * the thing that forms one.
     */
    fun connectedBaseUrl(): String? = link?.let(::baseUrlOf)

    /**
     * Drops the link and everything that depended on it.
     *
     * The grants go with it, and that ordering matters: a grant that outlived the link it was
     * issued for would still authorise a request arriving over any *other* interface.
     */
    suspend fun disconnect() = linkMutex.withLock {
        link = null
        wifiDirect.disconnect()
        p2pServer.clearGrants()
        peers.clear()
    }

    private fun baseUrlOf(link: DirectLink) =
        "http://${link.hostAddress}:${com.wander.android.data.sources.agro.LocalNetwork.P2P_PORT}"
}
