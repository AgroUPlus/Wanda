package com.wander.android.core.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Where an off-grid link ended up, once one exists. */
internal data class DirectLink(
    /** The address serving the library: the group owner's. */
    val hostAddress: String,
    /** True when this device is the one serving. */
    val isHost: Boolean
)

/**
 * Raises a Wi-Fi radio link between two devices with no router, no internet and no account.
 *
 * This is the transport tier the whole feature exists for, and it is worth being precise about what
 * it adds. Tier 4 already streams between devices at full speed **when they share a network**. This
 * is for when they do not: a car, a plane, a festival, a power cut, a country whose network you do
 * not want to be on. The link is a direct 5 GHz association between two radios; nothing is uploaded
 * anywhere and nothing traverses a third party.
 *
 * Once a group exists, the peer has an ordinary IP address, and everything above this — the HTTP
 * server on 8702, the grants, the resolver — works unchanged. **That is the design.** The
 * alternative, a bespoke transfer protocol over the Wi-Fi Direct socket, would have meant a second
 * implementation of range requests, authorisation and seeking, for no gain.
 *
 * ## What this cannot promise
 *
 * Wi-Fi Direct is the least uniform API on Android. Group formation shows a system dialog on most
 * devices; some manufacturers show nothing and silently fail; a device already on 2.4 GHz Wi-Fi may
 * negotiate the group there and give a fraction of the advertised speed. So every call here can
 * fail for reasons that are not bugs, and the callers must treat an off-grid link as an
 * opportunity, never as something to wait on.
 */
@Singleton
internal class WifiDirectLink @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val manager: WifiP2pManager?
        get() = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    val isAvailable: Boolean
        get() = manager != null &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)

    /**
     * Makes this device visible to Wi-Fi Direct. Nothing more, and deliberately nothing more.
     *
     * Being findable has to be cheap, because *both* devices must be findable before either can
     * see the other. An earlier version created a group here as well, which meant being findable
     * made you a group owner — and a group owner cannot join anybody else's group, so two findable
     * devices could never connect to each other. Holding an open Wi-Fi Direct group on both sides
     * also spends a radio for a link that at most one of them will use.
     *
     * Ownership is decided at the moment somebody taps, by negotiation, with the tapper asking to
     * be the client. See [connect].
     */
    @SuppressLint("MissingPermission")
    fun makeDiscoverable() {
        // Never during a negotiation: a scan started while a group is being formed puts the
        // supplicant back into discovery and the negotiation stalls where it stands.
        if (isConnecting) return
        val manager = manager ?: return
        val channel = manager.initialize(context, context.mainLooper, null) ?: return
        manager.discoverPeers(channel, null)
    }

    /**
     * True from the first scan of a connection attempt until it has a link or has given up.
     *
     * Guards [makeDiscoverable] rather than the framework: the sharing side keeps asking to be
     * discoverable for as long as its screen is open, and one of those calls landing mid-negotiation
     * is enough to stall it.
     */
    @Volatile
    private var isConnecting: Boolean = false

    /**
     * Forms a group and waits for it to carry an address, or gives up.
     *
     * The timeout is the point of the whole method. Group formation involves a user tapping a
     * system dialog on the other device, and there is no callback for "they walked away" — without
     * a deadline this suspends until the process dies.
     */
    @SuppressLint("MissingPermission")
    internal suspend fun connect(timeoutMs: Long = CONNECT_TIMEOUT_MS): DirectLink? {
        val manager = manager ?: return null
        val channel = manager.initialize(context, context.mainLooper, null) ?: return null

        isConnecting = true
        return try {
            connectInner(manager, channel, timeoutMs)
        } finally {
            isConnecting = false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectInner(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        timeoutMs: Long
    ): DirectLink? {
        return withTimeoutOrNull(timeoutMs) {
            // Any group this device is still holding goes first.
            //
            // Nothing creates one on the findable path any more, but an abandoned attempt can
            // leave one behind, and Wi-Fi Direct refuses to let a group owner join somebody else's
            // group. `dumpsys wifip2p` shows that refusal as
            // `CONNECT processed=GroupCreatedState dest=<null>` — no transition at all, which
            // reads exactly like the connect never happening.
            leaveOwnGroup(manager, channel)

            // Discovery first. `connect` needs a peer's device address, and the peer list is empty
            // until a scan has run — this used to call `createGroup`, which asks for no peer at
            // all: the device formed a group of one, became its owner, and `groupOwnerAddress` was
            // then its *own* address. Everything afterwards talked to itself, which the pairing
            // check caught as "the link reached a different device".
            val peers = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
                discoverPeers(manager, channel)
            }.orEmpty()
            if (peers.isEmpty()) return@withTimeoutOrNull null

            // Discovery is deliberately *not* stopped here, though stopping it looks like the
            // tidy thing to do and was tried. `stopPeerDiscovery` flushes the framework's peer
            // cache, so the address handed to `connect` a moment later names a device it no longer
            // knows: the request is refused instantly and the state machine never leaves
            // `InactiveState`. What must not happen is a *new* scan during negotiation, and
            // [isConnecting] is what prevents that.
            for (device in peers) {
                val config = android.net.wifi.p2p.WifiP2pConfig().apply {
                    deviceAddress = device.deviceAddress
                    // Zero: this device asks to be the client, so the peer becomes the owner.
                    //
                    // Not a preference but a requirement. The framework only ever hands back the
                    // *owner's* address, so if this device won ownership the address would be its
                    // own and every fetch would go to itself.
                    groupOwnerIntent = 0
                }
                val asked = suspendCancellableCoroutine { continuation ->
                    manager.connect(
                        channel,
                        config,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() = continuation.resume(true)
                            override fun onFailure(reason: Int) = continuation.resume(false)
                        }
                    )
                }
                if (!asked) continue
                // Longer than it feels it should be. Provision discovery waits for a person to
                // notice a system dialog and tap it, and only then does negotiation begin — the
                // first attempt spent eight seconds on the tap and was killed twenty seconds into
                // a negotiation that had not finished.
                val link = withTimeoutOrNull(NEGOTIATION_TIMEOUT_MS) {
                    awaitConnection(manager, channel)
                }
                if (link != null) return@withTimeoutOrNull link
            }
            null
        }
    }

    /**
     * The Wi-Fi Direct devices in range, once a scan has answered.
     *
     * Separate from the BLE list on purpose, and unmatched to it: a beacon is ten bytes and cannot
     * carry a MAC address, so there is no way to know which of these is the phone the user tapped.
     * The caller connects, then checks who it reached — see `OffGridPairing`.
     */
    @SuppressLint("MissingPermission")
    private suspend fun discoverPeers(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ): List<android.net.wifi.p2p.WifiP2pDevice> = suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context?, intent: Intent?) {
                if (intent?.action != WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) return
                manager.requestPeers(channel) { list ->
                    val found = list?.deviceList?.toList().orEmpty()
                    if (found.isEmpty()) return@requestPeers
                    runCatching { context.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resume(found)
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        manager.discoverPeers(channel, null)
    }

    /**
     * Waits for the framework to say the group has an owner with an address.
     *
     * Driven by the broadcast rather than polled: `requestConnectionInfo` immediately after
     * `createGroup` returns a group with no address at all, and a poll loop around that is a
     * battery cost and a race in one.
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitConnection(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ): DirectLink? = suspendCancellableCoroutine { continuation ->
        // Asked once up front as well as listened for. The broadcast is the reliable path, but a
        // group that formed between `connect` returning and this receiver being registered would
        // never broadcast again, and the wait would run to its timeout beside a working link.
        //
        // `!isGroupOwner` is not a detail: while this device owns a group, the framework answers
        // "group formed" with *our own* address, and this probe would resolve the wait instantly
        // onto ourselves. By design the peer owns the group — see [hostGroup].
        manager.requestConnectionInfo(channel) { info ->
            val address = info?.groupOwnerAddress?.hostAddress
            if (info?.groupFormed == true && !info.isGroupOwner &&
                !address.isNullOrBlank() && continuation.isActive
            ) {
                continuation.resume(DirectLink(address, isHost = false))
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context?, intent: Intent?) {
                if (intent?.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
                manager.requestConnectionInfo(channel) { info ->
                    val address = info?.groupOwnerAddress?.hostAddress
                    // Same guard as above: a group we own names us, not the peer.
                    if (info?.groupFormed == true && !info.isGroupOwner && !address.isNullOrBlank()) {
                        runCatching { context.unregisterReceiver(this) }
                        if (continuation.isActive) {
                            continuation.resume(DirectLink(address, isHost = false))
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
    }

    /**
     * Stands down as a group owner, and waits for the framework to agree that we have.
     *
     * The wait is the point. `removeGroup` is asynchronous, and a `connect` issued before the state
     * machine has left `GroupCreatedState` is refused exactly as it was before — the fix would look
     * like no fix at all. A device that owned no group answers immediately.
     */
    @SuppressLint("MissingPermission")
    private suspend fun leaveOwnGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ) {
        withTimeoutOrNull(GROUP_REMOVAL_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                manager.removeGroup(
                    channel,
                    object : WifiP2pManager.ActionListener {
                        // Failure is the ordinary answer when there was nothing to remove.
                        override fun onSuccess() = continuation.resume(Unit)
                        override fun onFailure(reason: Int) = continuation.resume(Unit)
                    }
                )
            }
        }
    }

    /** Tears the group down. A group left up is a radio left on, and it will not stop by itself. */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        val manager = manager ?: return
        val channel = manager.initialize(context, context.mainLooper, null) ?: return
        manager.removeGroup(channel, null)
    }

    private companion object {
        /**
         * The whole exchange: scan, a person tapping a dialog, and group formation.
         *
         * Long, because every part of it is. Short enough that a device that was never going to
         * answer does not hold the radio open indefinitely.
         */
        const val CONNECT_TIMEOUT_MS = 120_000L

        /** Standing down is quick or it is stuck; either way the connect attempt should proceed. */
        const val GROUP_REMOVAL_TIMEOUT_MS = 5_000L

        /** A scan answers in a second or two or not at all; waiting longer finds nothing new. */
        const val DISCOVERY_TIMEOUT_MS = 12_000L

        /**
         * From "connect" to an address, including the tap on the other phone.
         *
         * Measured rather than guessed: on a Pixel 10 and an S22 the tap landed eight seconds in
         * and negotiation was still going twenty seconds after that.
         */
        const val NEGOTIATION_TIMEOUT_MS = 75_000L
    }
}
