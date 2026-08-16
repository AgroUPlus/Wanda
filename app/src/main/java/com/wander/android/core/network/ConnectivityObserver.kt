package com.wander.android.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this device currently has a usable internet connection.
 *
 * There was no such thing before: offline mode was a switch the user had to find in Settings and
 * flip by hand, and nothing in the app reacted to the network actually going away. A remote source
 * simply kept being asked for data it could not fetch.
 *
 * This is a callback, not a poll — the framework tells us when the default network changes, so
 * knowing the answer costs nothing while the answer is not changing, which is what the project's
 * battery rules require. The flow is shared eagerly because playback and the repository both need a
 * correct value the instant they ask, not one subscription later.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val manager = context.getSystemService<ConnectivityManager>()

    val isOnline: StateFlow<Boolean> = callbackFlow {
        val cm = manager
        if (cm == null) {
            // No ConnectivityManager means we cannot know. Claiming "offline" would mute every
            // remote source on a device that may well be online.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(cm.hasInternet(network))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                // A network can exist long before it validates — a captive portal being the usual
                // case. `NET_CAPABILITY_VALIDATED` is the difference between "connected" and
                // "connected to something that can reach the internet".
                trySend(capabilities.isUsable())
            }
        }

        trySend(cm.hasInternet(cm.activeNetwork))
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, manager?.hasInternet(manager.activeNetwork) ?: true)
}

private fun ConnectivityManager.hasInternet(network: Network?): Boolean {
    val capabilities = getNetworkCapabilities(network ?: return false) ?: return false
    return capabilities.isUsable()
}

private fun NetworkCapabilities.isUsable(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
