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

        return withTimeoutOrNull(timeoutMs) {
            val created = suspendCancellableCoroutine { continuation ->
                manager.createGroup(
                    channel,
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() = continuation.resume(true)
                        override fun onFailure(reason: Int) = continuation.resume(false)
                    }
                )
            }
            if (!created) return@withTimeoutOrNull null
            awaitConnection(manager, channel)
        }
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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context?, intent: Intent?) {
                if (intent?.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
                manager.requestConnectionInfo(channel) { info ->
                    val address = info?.groupOwnerAddress?.hostAddress
                    if (info?.groupFormed == true && !address.isNullOrBlank()) {
                        runCatching { context.unregisterReceiver(this) }
                        if (continuation.isActive) {
                            continuation.resume(DirectLink(address, info.isGroupOwner))
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

    /** Tears the group down. A group left up is a radio left on, and it will not stop by itself. */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        val manager = manager ?: return
        val channel = manager.initialize(context, context.mainLooper, null) ?: return
        manager.removeGroup(channel, null)
    }

    private companion object {
        /**
         * Long enough for somebody to notice a dialog and tap it, short enough that a device that
         * was never going to answer does not hold the radio open.
         */
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
