package com.wander.android.ui

import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Which way the network just turned, and so what there is to offer the user. */
enum class NetworkPrompt { GO_OFFLINE, GO_ONLINE }

/**
 * How long the network has to hold a state before the app believes it. A lift, a tunnel or a
 * Wi-Fi/mobile handover all flap, and each flap would otherwise be a dialog.
 */
private const val NetworkSettleMs = 5_000L

/**
 * Observes connectivity transitions and coordinates prompts to toggle offline mode when signal drops
 * or returns.
 */
class NetworkPromptCoordinator @Inject constructor(
    private val connectivity: ConnectivityObserver,
    private val secureStorage: SecureStorage
) {
    private val dismissedPrompt = MutableStateFlow<NetworkPrompt?>(null)

    fun createOfflinePlaybackFlow(scope: CoroutineScope): StateFlow<Boolean> =
        combine(
            connectivity.isOnline,
            secureStorage.isOfflineMode
        ) { online, offlineMode -> offlineMode || !online }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    @OptIn(FlowPreview::class)
    fun createNetworkPromptFlow(scope: CoroutineScope): StateFlow<NetworkPrompt?> =
        combine(
            connectivity.isOnline.drop(1).debounce(NetworkSettleMs),
            secureStorage.isOfflineMode,
            dismissedPrompt
        ) { online, offlineMode, dismissed ->
            val prompt = when {
                !online && !offlineMode -> NetworkPrompt.GO_OFFLINE
                online && offlineMode -> NetworkPrompt.GO_ONLINE
                else -> null
            }
            prompt?.takeIf { it != dismissed }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun acceptPrompt(prompt: NetworkPrompt) {
        secureStorage.setOfflineMode(prompt == NetworkPrompt.GO_OFFLINE)
        dismissedPrompt.value = prompt
    }

    fun dismissPrompt(prompt: NetworkPrompt) {
        dismissedPrompt.value = prompt
    }
}
