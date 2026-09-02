package com.wander.android.core.p2p

import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.sync.P2PServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val p2pServer: P2PServer,
    private val pairing: OffGridPairing
) {

    private val peers = NearbyPeers()
    private val linkMutex = Mutex()
    private var link: DirectLink? = null

    private val _outgoingLink = kotlinx.coroutines.flow.MutableStateFlow<OffGridLink?>(null)

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /** Watches the link this device made, so a peer that walks off stops being shown as present. */
    private var watchdog: kotlinx.coroutines.Job? = null

    /**
     * Whether this device is linked to another, from either end.
     *
     * Both ends, and that is the point. A link had exactly one observer — the phone that tapped —
     * so the phone that was tapped raised a group and served audio with nothing on its screen
     * saying it was connected to anyone, and neither side could tell a peer that had left from one
     * that had never arrived.
     *
     * The two halves come from different places because they are different facts. This device
     * connecting outwards is something it did and can simply remember; another device connecting
     * inwards is only visible as a pairing grant the server issued, which is why
     * [P2PServer.pairedPeers] exists.
     */
    val links: Flow<List<OffGridLink>> =
        kotlinx.coroutines.flow.combine(
            _outgoingLink,
            p2pServer.pairedPeers
        ) { outgoing, paired ->
            val incoming = paired.map { peer ->
                OffGridLink(
                    deviceId = OffGridBeacon.deviceIdFrom(
                        runCatching {
                            android.util.Base64.decode(
                                peer.publicKeyB64,
                                android.util.Base64.NO_WRAP
                            )
                        }.getOrDefault(ByteArray(0))
                    ),
                    role = OffGridLink.Role.ACCEPTED,
                    sinceMs = peer.pairedAtMs
                )
            }
            // The outgoing link first: it is the one this user chose, and on the rare phone that is
            // both ends of two links at once it is the one they are waiting on.
            (listOfNotNull(outgoing) + incoming).distinctBy { it.deviceId }
        }.distinctUntilChanged()

    /**
     * The bearer the peer issued face to face, good for as long as the link is.
     *
     * Held here rather than fetched per track: pairing is one round trip and the grant outlives a
     * single song, so asking again on every track change would spend the radio to re-learn what is
     * already known.
     */
    private var grant: String? = null

    /** Whether this device can do any of it. False on a phone without BLE peripheral support. */
    val isSupported: Boolean
        get() = ble.isAvailable && wifiDirect.isAvailable

    /**
     * Starts telling the room this device is here.
     *
     * [servesAudio] is a promise about what a peer will find if it connects, so it is set from
     * whether the server is actually running rather than from an intention to run it.
     */
    suspend fun startAdvertising(servesAudio: Boolean): Result<Unit> {
        // Discovery only. Being findable must stay cheap: both devices have to be findable before
        // either can see the other, so anything expensive here is paid twice for one link.
        //
        // Its failure is logged by the framework wrapper but not returned: BLE is what makes this
        // device appear on the other phone's screen, and a Wi-Fi Direct scan that would not start
        // is a problem for the tap that comes later, not a reason to refuse to be seen now.
        wifiDirect.makeDiscoverable()
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
     *
     * Sightings alone are not enough to drive it, though, and that was the bug: a device that walks
     * out of the room stops advertising, so nothing arrives, so nothing re-emits, so
     * [NearbyPeers.current]'s expiry sweep never runs and the departed phone stays on screen for
     * good. Absence is silence, and silence has to be checked for on a clock. The ticker runs only
     * while this flow is collected, and `distinctUntilChanged` keeps the busy case — dozens of
     * sightings a second from one phone in the room — from recomposing the list each time.
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
     *
     * Not `==`, which would compare [NearbyPeers.Peer.lastSeenAtMs] and so be different on every
     * single sighting — dozens a second from one phone sitting in the room, each one a recomposition
     * of the list. What the screen actually shows is who is there, in what order, and how strong the
     * signal is in words, so the signal is bucketed well below the jitter that a stationary phone
     * produces and well above the change that would alter the word.
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
     *
     * A failure here is ordinary rather than exceptional — see [WifiDirectLink] on how many ways
     * group formation fails without anything being wrong — but it now carries the reason, because
     * "the scan was refused" and "nobody is here" want different things from the user.
     */
    suspend fun connect(peer: NearbyPeers.Peer): Result<String> = linkMutex.withLock {
        link?.let { existing ->
            if (grant != null) return@withLock Result.success(baseUrlOf(existing))
            // A link with no grant is a half-finished attempt, not something to build a second
            // link on top of. Wi-Fi Direct will not form a group while this device is in one, so
            // stacking the next attempt on it would fail for a reason that had nothing to do with
            // the peer.
            link = null
            wifiDirect.disconnect()
        }
        val formed = wifiDirect.connect().getOrElse { return@withLock Result.failure(it) }
        val base = baseUrlOf(formed)

        // Paired before the link is kept. A group formed with the wrong device is worse than no
        // group: it looks connected, and every fetch afterwards would go to a stranger.
        val token = pairing.pair(base, peer.beacon).getOrElse { error ->
            wifiDirect.disconnect()
            return@withLock Result.failure(error)
        }
        link = formed
        grant = token
        _outgoingLink.value = OffGridLink(
            deviceId = peer.beacon.deviceId,
            role = OffGridLink.Role.INITIATED,
            sinceMs = System.currentTimeMillis()
        )
        startWatchdog(base)
        Result.success(base)
    }

    /**
     * The grant this device holds for the peer it is linked to, or null.
     *
     * What lets [com.wander.android.data.repository.ListenAlongResolver] use tier 5 without Agro:
     * every other tier's token is minted by the server, and off-grid there is no server to mint
     * one.
     */
    fun grantToken(): String? = grant

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
     * The pairing grants go with it, and that ordering matters: a face-to-face grant that outlived
     * the link it was issued for would still authorise a request arriving over any *other*
     * interface. Agro's grants are left alone — they were never issued for this link, and taking
     * them down here broke LAN streaming every time somebody stopped sharing.
     */
    private fun startWatchdog(base: String) {
        watchdog?.cancel()
        watchdog = scope.launch {
            var missed = 0
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (link == null) return@launch
                // Counted rather than acted on at the first failure. Wi-Fi Direct drops a packet
                // when a phone's screen turns off or the radio changes channel, and tearing a link
                // down over one missed ping would make the feature look broken while working.
                missed = if (pairing.ping(base)) 0 else missed + 1
                if (missed >= MISSED_BEATS_BEFORE_DROP) {
                    // Torn down locally. The peer is not answering, so there is nobody to tell.
                    linkMutex.withLock {
                        link = null
                        grant = null
                        _outgoingLink.value = null
                    }
                    wifiDirect.disconnect()
                    return@launch
                }
            }
        }
    }

    suspend fun disconnect() = linkMutex.withLock {
        watchdog?.cancel()
        watchdog = null
        // Told before it is dropped, so the other phone's screen changes at the same moment this
        // one's does. Best effort: the usual reason to disconnect is that the peer has already
        // gone, and a teardown that waited on an unreachable peer would hang precisely then.
        link?.let { runCatching { pairing.unpair(baseUrlOf(it)) } }
        link = null
        grant = null
        _outgoingLink.value = null
        wifiDirect.disconnect()
        p2pServer.clearPairingGrants()
        peers.clear()
    }

    /**
     * Ends one link, leaving advertising and any other link alone.
     *
     * The two roles end differently, and that asymmetry is real rather than an inconsistency. A
     * link this device *made* is a Wi-Fi Direct group it negotiated, so ending it means tearing the
     * group down. A link another device made to this one is a grant this device issued; the group
     * belongs to them, and all this end can do — and should do — is stop honouring the grant. Their
     * screen updates when their next request is refused, or immediately if they are still there to
     * be told.
     */
    suspend fun disconnect(link: OffGridLink) {
        when (link.role) {
            OffGridLink.Role.INITIATED -> disconnect()
            OffGridLink.Role.ACCEPTED -> p2pServer.revokePairing(link.deviceId)
        }
    }

    private companion object {
        /**
         * How often the list is re-swept when nothing is being heard.
         *
         * Well under `NearbyPeers.STALE_AFTER_MS`, so a departure shows within a tick or two of
         * becoming true, and far too slow to be a poll worth worrying about.
         */
        const val SWEEP_INTERVAL_MS = 2_000L

        /** Coarser than a stationary phone's jitter, finer than any word the screen puts on it. */
        const val RSSI_BUCKET_DBM = 10

        /**
         * How often a link this device made is checked.
         *
         * Five seconds is a cheap request over a radio that is already up, and it bounds how long
         * a screen can claim a connection that has gone — which was previously forever.
         */
        const val HEARTBEAT_INTERVAL_MS = 5_000L

        /** Three misses, so a dropped packet or a sleeping screen is not read as a departure. */
        const val MISSED_BEATS_BEFORE_DROP = 3
    }

    private fun baseUrlOf(link: DirectLink) =
        "http://${link.hostAddress}:${com.wander.android.data.sources.agro.LocalNetwork.P2P_PORT}"
}
