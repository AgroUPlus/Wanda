package com.wander.android.core.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The `WifiP2pManager` calls, as suspending functions that answer.
 *
 * Every method on that manager is fire-and-forget with a callback, and the callback is the only
 * place a refusal is ever mentioned. Passing `null` for it — which this code did in three places —
 * turns "the user declined the nearby-devices permission" and "location services are switched off",
 * the two most common ways this feature fails, into an empty peer list indistinguishable from an
 * empty room. So nothing here takes a nullable listener: a call either succeeds or says why not.
 *
 * Split out of [WifiDirectLink] so that class stays policy — who hosts, who asks, how long to wait
 * — and this stays the framework's shape.
 */

/** What a connection attempt settled into. */
internal sealed interface LinkOutcome {
    /** The peer owns the group and is reachable here. The good case. */
    data class Client(val hostAddress: String) : LinkOutcome

    /**
     * A group formed, but *this* device owns it.
     *
     * Not usable and not ignorable. The framework only ever reports the owner's address, so a group
     * we own names us, and every fetch would go to ourselves. It is reported rather than waited out
     * because the alternative — the guard that used to sit here silently declining to resolve — was
     * a seventy-five second stall ending with the group still standing.
     */
    data object OwnedGroup : LinkOutcome
}

/** Runs one `ActionListener` call and reports the framework's answer. */
private suspend fun awaitAction(
    name: String,
    call: (WifiP2pManager.ActionListener) -> Unit
): Result<Unit> = suspendCancellableCoroutine { continuation ->
    call(object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            if (continuation.isActive) continuation.resume(Result.success(Unit))
        }

        override fun onFailure(reason: Int) {
            if (continuation.isActive) {
                continuation.resume(Result.failure(IOException("$name refused: ${describe(reason)}")))
            }
        }
    })
}

/** The framework's refusal codes, in terms of what is actually wrong. */
internal fun describe(reason: Int): String = when (reason) {
    WifiP2pManager.P2P_UNSUPPORTED -> "this phone does not support Wi-Fi Direct"
    WifiP2pManager.BUSY -> "the Wi-Fi Direct radio is busy"
    WifiP2pManager.ERROR -> "the Wi-Fi Direct framework returned an error"
    else -> "reason $reason"
}

/** Asks the framework to scan. Failure here is usually a permission, not a quiet room. */
@SuppressLint("MissingPermission")
internal suspend fun WifiP2pManager.startDiscovery(
    channel: WifiP2pManager.Channel
): Result<Unit> = awaitAction("discoverPeers") { discoverPeers(channel, it) }

/**
 * The Wi-Fi Direct devices in range, once a scan has answered.
 *
 * Unmatched to the BLE list on purpose: a beacon is ten bytes and cannot carry a MAC address, so
 * there is no way to know which of these is the phone the user tapped. The caller connects, then
 * checks who it reached — see [OffGridPairing].
 */
@SuppressLint("MissingPermission")
internal suspend fun WifiP2pManager.awaitPeers(
    context: Context,
    channel: WifiP2pManager.Channel
): List<WifiP2pDevice> = suspendCancellableCoroutine { continuation ->
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(receivedContext: Context?, intent: Intent?) {
            if (intent?.action != WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) return
            requestPeers(channel) { list ->
                val found = list?.deviceList?.toList().orEmpty()
                if (found.isEmpty()) return@requestPeers
                runCatching { context.unregisterReceiver(this) }
                if (continuation.isActive) continuation.resume(found)
            }
        }
    }

    // Registered before the scan is asked for, not after. A framework that already had peers
    // cached answers the moment discovery starts, and a receiver put up afterwards would miss that
    // broadcast and then wait out the timeout beside a list that was ready all along.
    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION),
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
    continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

    discoverPeers(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() = Unit

        // The failure that matters most, and the one a null listener used to swallow: a declined
        // nearby-devices grant or location switched off refuses here, and without this the caller
        // saw only an empty peer list and told the user to move closer.
        override fun onFailure(reason: Int) {
            runCatching { context.unregisterReceiver(receiver) }
            if (continuation.isActive) {
                continuation.resumeWithException(
                    IOException("the scan for nearby phones was refused: ${describe(reason)}")
                )
            }
        }
    })
}

/** Asks [device] to form a group with this device as the client. */
@SuppressLint("MissingPermission")
internal suspend fun WifiP2pManager.requestLink(
    channel: WifiP2pManager.Channel,
    device: WifiP2pDevice
): Result<Unit> {
    val config = WifiP2pConfig().apply {
        deviceAddress = device.deviceAddress
        // Zero: this device asks to be the client, so the peer becomes the owner.
        //
        // A request, not a guarantee — which is why [LinkOutcome.OwnedGroup] exists.
        groupOwnerIntent = 0
    }
    return awaitAction("connect") { connect(channel, config, it) }
}

/**
 * Waits for the framework to say the group has an owner with an address.
 *
 * Driven by the broadcast rather than polled: `requestConnectionInfo` immediately after a connect
 * returns a group with no address at all, and a poll loop around that is a battery cost and a race
 * in one. Asked once up front as well, because a group that formed between the connect returning
 * and this receiver being registered would never broadcast again.
 */
@SuppressLint("MissingPermission")
internal suspend fun WifiP2pManager.awaitLink(
    context: Context,
    channel: WifiP2pManager.Channel
): LinkOutcome = suspendCancellableCoroutine { continuation ->
    fun settle(info: android.net.wifi.p2p.WifiP2pInfo?): LinkOutcome? {
        if (info?.groupFormed != true) return null
        if (info.isGroupOwner) return LinkOutcome.OwnedGroup
        val address = info.groupOwnerAddress?.hostAddress
        return if (address.isNullOrBlank()) null else LinkOutcome.Client(address)
    }

    requestConnectionInfo(channel) { info ->
        val outcome = settle(info)
        if (outcome != null && continuation.isActive) continuation.resume(outcome)
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(receivedContext: Context?, intent: Intent?) {
            if (intent?.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
            requestConnectionInfo(channel) { info ->
                val outcome = settle(info) ?: return@requestConnectionInfo
                runCatching { context.unregisterReceiver(this) }
                if (continuation.isActive) continuation.resume(outcome)
            }
        }
    }

    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION),
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
    continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
}

/**
 * Stands down as a group owner, and waits for the framework to agree that we have.
 *
 * The wait is the point. `removeGroup` is asynchronous, and a `connect` issued before the state
 * machine has left `GroupCreatedState` is refused exactly as it was before — the fix would look
 * like no fix at all. A device that owned no group is refused too, and that refusal is the
 * ordinary answer rather than a problem, so the result is deliberately discarded.
 */
@SuppressLint("MissingPermission")
internal suspend fun WifiP2pManager.leaveGroup(channel: WifiP2pManager.Channel) {
    awaitAction("removeGroup") { removeGroup(channel, it) }
}
