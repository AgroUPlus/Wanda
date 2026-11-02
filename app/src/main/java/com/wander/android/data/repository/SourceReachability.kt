package com.wander.android.data.repository

import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.network.LocalNetworkHost
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.SourceType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Whether a source is worth asking right now: one rule for playback, browsing and search.
 *
 * Validated internet reaches every backend. Without it, a Navidrome server on the LAN or a tailnet
 * still answers on any network at all — a home Wi-Fi whose uplink is down, a travel router — so it
 * stays reachable there. Playback applied that rule while browsing and search did not, which hid
 * the very server the player was still streaming from. Offline mode reaches local files only.
 */
internal class SourceReachability(
    private val connectivity: ConnectivityObserver,
    private val secureStorage: SecureStorage
) {
    fun isReachable(type: SourceType): Boolean {
        if (type == SourceType.LOCAL) return true
        if (secureStorage.isOfflineMode.value) return false
        if (connectivity.isOnline.value) return true
        if (type != SourceType.NAVIDROME || !connectivity.hasAnyNetwork) return false
        val host = secureStorage.navidromeServerUrl.toHttpUrlOrNull()?.host ?: return false
        return LocalNetworkHost.isLocal(host)
    }
}
