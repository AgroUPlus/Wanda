package com.wander.android.core.p2p

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
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
 * The framework calls themselves live in `WifiP2pCalls.kt`; what is left here is the policy.
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
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    /**
     * The one channel, kept for as long as the framework will honour it.
     *
     * Every call must go through the same one. `initialize` was being called afresh in three
     * places, which meant `disconnect` asked a channel that had never joined anything to leave it,
     * and the peer list a scan had filled belonged to a channel already thrown away. A channel is
     * this app's registration with the Wi-Fi P2P service, not a handle to pass around.
     *
     * Dropped on `onChannelDisconnected` so the next call re-registers rather than talking to a
     * framework that has forgotten us.
     */
    @Volatile
    private var channel: WifiP2pManager.Channel? = null

    @Synchronized
    private fun channel(manager: WifiP2pManager): WifiP2pManager.Channel? {
        channel?.let { return it }
        val opened = manager.initialize(context, context.mainLooper) {
            Log.w(TAG, "the Wi-Fi Direct channel was dropped; it will be reopened on next use")
            channel = null
        } ?: return null
        channel = opened
        return opened
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
    suspend fun makeDiscoverable(): Result<Unit> {
        // Never during a negotiation: a scan started while a group is being formed puts the
        // supplicant back into discovery and the negotiation stalls where it stands.
        if (isConnecting) return Result.success(Unit)
        val manager = manager ?: return Result.failure(IOException("no Wi-Fi Direct on this phone"))
        val channel = channel(manager)
            ?: return Result.failure(IOException("Wi-Fi Direct could not be initialised"))
        return manager.startDiscovery(channel)
    }

    /**
     * Forms a group and waits for it to carry an address, or gives up.
     *
     * The timeout is the point of the whole method. Group formation involves a user tapping a
     * system dialog on the other device, and there is no callback for "they walked away" — without
     * a deadline this suspends until the process dies.
     */
    internal suspend fun connect(timeoutMs: Long = CONNECT_TIMEOUT_MS): Result<DirectLink> {
        val manager = manager ?: return Result.failure(IOException("no Wi-Fi Direct on this phone"))
        val channel = channel(manager)
            ?: return Result.failure(IOException("Wi-Fi Direct could not be initialised"))

        isConnecting = true
        return try {
            withTimeoutOrNull(timeoutMs) { connectInner(manager, channel) }
                ?: Result.failure(IOException("the other phone did not answer in time"))
        } finally {
            isConnecting = false
        }
    }

    private suspend fun connectInner(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ): Result<DirectLink> {
        // Any group this device is still holding goes first.
        //
        // Nothing creates one on the findable path any more, but an abandoned attempt can leave one
        // behind, and Wi-Fi Direct refuses to let a group owner join somebody else's group.
        // `dumpsys wifip2p` shows that refusal as `CONNECT processed=GroupCreatedState dest=<null>`
        // — no transition at all, which reads exactly like the connect never happening.
        withTimeoutOrNull(GROUP_REMOVAL_TIMEOUT_MS) { manager.leaveGroup(channel) }

        // Discovery first. `connect` needs a peer's device address, and the peer list is empty
        // until a scan has run — this used to call `createGroup`, which asks for no peer at all:
        // the device formed a group of one, became its owner, and `groupOwnerAddress` was then its
        // *own* address. Everything afterwards talked to itself, which the pairing check caught as
        // "the link reached a different device".
        val peers = runCatching {
            withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { manager.awaitPeers(context, channel) }
        }.getOrElse { return Result.failure(it) }

        // Told apart on purpose. A refused scan throws above and names the permission; an empty
        // room answers nothing at all, and the advice for the two is not the same.
        if (peers.isNullOrEmpty()) {
            return Result.failure(IOException("no other phone was found nearby"))
        }

        // Discovery is deliberately *not* stopped here, though stopping it looks like the tidy
        // thing to do and was tried. `stopPeerDiscovery` flushes the framework's peer cache, so the
        // address handed to `connect` a moment later names a device it no longer knows: the request
        // is refused instantly and the state machine never leaves `InactiveState`. What must not
        // happen is a *new* scan during negotiation, and [isConnecting] is what prevents that.
        var lastFailure: Throwable? = null
        for (device in peers) {
            val asked = manager.requestLink(channel, device)
            if (asked.isFailure) {
                lastFailure = asked.exceptionOrNull()
                continue
            }
            // Longer than it feels it should be. Provision discovery waits for a person to notice a
            // system dialog and tap it, and only then does negotiation begin — the first attempt
            // spent eight seconds on the tap and was killed twenty seconds into a negotiation that
            // had not finished.
            when (val outcome = withTimeoutOrNull(NEGOTIATION_TIMEOUT_MS) {
                manager.awaitLink(context, channel)
            }) {
                is LinkOutcome.Client ->
                    return Result.success(DirectLink(outcome.hostAddress, isHost = false))

                // We won ownership despite asking not to. The group is useless — it names us — and
                // it is holding the radio, so it goes before the next device is tried.
                LinkOutcome.OwnedGroup -> {
                    Log.w(TAG, "this phone became the group owner; standing down and trying again")
                    withTimeoutOrNull(GROUP_REMOVAL_TIMEOUT_MS) { manager.leaveGroup(channel) }
                    lastFailure = IOException("this phone ended up hosting instead of receiving")
                }

                null -> lastFailure = IOException("the other phone did not finish connecting")
            }
        }
        return Result.failure(lastFailure ?: IOException("no phone nearby would form a link"))
    }

    /** Tears the group down. A group left up is a radio left on, and it will not stop by itself. */
    suspend fun disconnect() {
        val manager = manager ?: return
        val channel = channel(manager) ?: return
        withTimeoutOrNull(GROUP_REMOVAL_TIMEOUT_MS) { manager.leaveGroup(channel) }
    }

    private companion object {
        const val TAG = "WifiDirectLink"

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
