package com.wander.android.core.p2p

import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.sync.P2PServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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
    private val p2pServer: P2PServer,
    private val pairing: OffGridPairing,
    private val linkManager: OffGridLinkManager
) {

    constructor(
        ble: BleDiscovery,
        wifiDirect: WifiDirectLink,
        identityKeys: IdentityKeyManager,
        p2pServer: P2PServer,
        pairing: OffGridPairing
    ) : this(
        ble,
        wifiDirect,
        identityKeys,
        p2pServer,
        pairing,
        OffGridLinkManager(wifiDirect, p2pServer, pairing)
    )

    private val peers = NearbyPeers()
    private val _isAdvertising = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * Whether this device is currently findable.
     */
    val isAdvertising: kotlinx.coroutines.flow.StateFlow<Boolean> = _isAdvertising.asStateFlow()

    /**
     * Whether this device is linked to another, from either end.
     */
    val links: Flow<List<OffGridLink>> get() = linkManager.links

    /** Whether this device can do any of it. False on a phone without BLE peripheral support. */
    val isSupported: Boolean
        get() = ble.isAvailable && wifiDirect.isAvailable

    /**
     * Starts telling the room this device is here.
     */
    suspend fun startAdvertising(servesAudio: Boolean): Result<Unit> {
        wifiDirect.makeDiscoverable()
        val publicKey = identityKeys.getOrCreateIdentityKeys().second.encoded
        return ble.advertise(
            OffGridBeacon(
                deviceId = OffGridBeacon.deviceIdFrom(publicKey),
                fingerprint = OffGridBeacon.fingerprintFrom(publicKey),
                servesAudio = servesAudio
            )
        ).onSuccess { _isAdvertising.value = true }
    }

    fun stopAdvertising() {
        _isAdvertising.value = false
        ble.stopAdvertising()
    }

    /**
     * Devices in the room that will serve audio, nearest first, updated as they are heard.
     */
    fun nearbyServers(): Flow<List<NearbyPeers.Peer>> {
        val sightings = ble.scan().map { (beacon, rssi) ->
            peers.sighted(beacon, rssi, System.currentTimeMillis())
        }
        val sweeps = flow {
            while (true) {
                delay(SWEEP_INTERVAL_MS)
                emit(false)
            }
        }
        return merge(sightings, sweeps)
            .map { peers.servers(System.currentTimeMillis()) }
            .distinctUntilChanged { old, new -> old.looksTheSameAs(new) }
    }

    /**
     * Whether two lists would draw identically.
     */
    private fun List<NearbyPeers.Peer>.looksTheSameAs(other: List<NearbyPeers.Peer>): Boolean {
        if (size != other.size) return false
        return indices.all { i ->
            this[i].beacon == other[i].beacon &&
                this[i].rssi / RSSI_BUCKET_DBM == other[i].rssi / RSSI_BUCKET_DBM
        }
    }

    /**
     * Raises a direct link and returns the base URL the peer's library is reachable at.
     */
    suspend fun connect(peer: NearbyPeers.Peer): Result<String> = linkManager.connect(peer)

    /**
     * The grant this device holds for the peer it is linked to, or null.
     */
    fun grantToken(): String? = linkManager.grantToken()

    /**
     * The base URL of a link that is *already* up, or null.
     */
    fun connectedBaseUrl(): String? = linkManager.connectedBaseUrl()

    suspend fun disconnect() {
        linkManager.disconnect()
        peers.clear()
    }

    /**
     * Ends one link, leaving advertising and any other link alone.
     */
    suspend fun disconnect(link: OffGridLink) {
        linkManager.disconnect(link)
    }

    private companion object {
        const val SWEEP_INTERVAL_MS = 2_000L
        const val RSSI_BUCKET_DBM = 10
    }
}
