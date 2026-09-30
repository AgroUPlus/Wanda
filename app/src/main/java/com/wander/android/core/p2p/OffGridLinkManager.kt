package com.wander.android.core.p2p

import com.wander.android.core.sync.P2PServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Wi-Fi Direct connections, session pairing grants, link lifecycle, and keep-alive watchdogs.
 */
@Singleton
internal class OffGridLinkManager @Inject constructor(
    private val wifiDirect: WifiDirectLink,
    private val p2pServer: P2PServer,
    private val pairing: OffGridPairing
) {
    private val linkMutex = Mutex()
    private var link: DirectLink? = null
    private val _outgoingLink = MutableStateFlow<OffGridLink?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdog: Job? = null
    private var grant: String? = null

    /**
     * Whether this device is linked to another, from either end.
     */
    val links: Flow<List<OffGridLink>> =
        combine(
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
            (listOfNotNull(outgoing) + incoming).distinctBy { it.deviceId }
        }.distinctUntilChanged()

    /**
     * Raises a direct link and returns the base URL the peer's library is reachable at.
     */
    suspend fun connect(peer: NearbyPeers.Peer): Result<String> = linkMutex.withLock {
        link?.let { existing ->
            if (grant != null) return@withLock Result.success(baseUrlOf(existing))
            link = null
            wifiDirect.disconnect()
        }
        val formed = wifiDirect.connect().getOrElse { return@withLock Result.failure(it) }
        val base = baseUrlOf(formed)

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
     */
    fun grantToken(): String? = grant

    /**
     * The base URL of a link that is *already* up, or null.
     */
    fun connectedBaseUrl(): String? = link?.let(::baseUrlOf)

    private fun startWatchdog(base: String) {
        watchdog?.cancel()
        watchdog = scope.launch {
            var missed = 0
            while (currentCoroutineContext().isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (link == null) return@launch
                missed = if (pairing.ping(base)) 0 else missed + 1
                if (missed >= MISSED_BEATS_BEFORE_DROP) {
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
        link?.let { runCatching { pairing.unpair(baseUrlOf(it)) } }
        link = null
        grant = null
        _outgoingLink.value = null
        wifiDirect.disconnect()
        p2pServer.clearPairingGrants()
    }

    suspend fun disconnect(link: OffGridLink) {
        when (link.role) {
            OffGridLink.Role.INITIATED -> disconnect()
            OffGridLink.Role.ACCEPTED -> p2pServer.revokePairing(link.deviceId)
        }
    }

    private fun baseUrlOf(link: DirectLink) =
        "http://${link.hostAddress}:${com.wander.android.data.sources.agro.LocalNetwork.P2P_PORT}"

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val MISSED_BEATS_BEFORE_DROP = 3
    }
}
