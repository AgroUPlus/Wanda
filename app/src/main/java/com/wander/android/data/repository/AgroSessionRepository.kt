package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.data.sources.agro.AgroGraphQl
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroLiveMessage
import com.wander.android.data.sources.agro.AgroNode
import com.wander.android.data.sources.agro.AgroSessionApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rest of the fleet is doing: the other registered devices, and the one session the
 * server holds per user.
 */
@Singleton
class AgroSessionRepository @Inject constructor(
    private val sessionApi: AgroSessionApi,
    private val graphQl: AgroGraphQl,
    private val connectivity: ConnectivityObserver,
    private val identityKeyManager: IdentityKeyManager
) {
    private val webSocket = AgroSessionWebSocket(graphQl, connectivity, identityKeyManager)

    private val _devices = MutableStateFlow<List<AgroNode>>(emptyList())
    val devices: StateFlow<List<AgroNode>> = _devices.asStateFlow()

    private val _incomingHandoff = MutableStateFlow<AgroHandoffState?>(null)
    val incomingHandoff: StateFlow<AgroHandoffState?> = _incomingHandoff.asStateFlow()

    private val _latestSession = MutableStateFlow<AgroHandoffState?>(null)
    val latestSession: StateFlow<AgroHandoffState?> = _latestSession.asStateFlow()

    private var dismissed: String? = null
    private var coldStartOfferAvailable = true

    suspend fun refresh() {
        if (!connectivity.isOnline.value) return

        if (!graphQl.isConfigured) {
            _devices.value = emptyList()
            _incomingHandoff.value = null
            _latestSession.value = null
            return
        }

        sessionApi.activeNodes().onSuccess { nodes ->
            _devices.value = nodes
                .filterNot { it.deviceId == graphQl.deviceId }
                .sortedByDescending { it.isOnline }
        }.onFailure { log("devices", it) }

        sessionApi.playbackHandoff()
            .onSuccess { handoff ->
                val fromElsewhere = handoff?.takeIf {
                    it.deviceId != graphQl.deviceId && it.trackTitle.isNotBlank()
                }
                if (fromElsewhere != null || handoff == null) _latestSession.value = fromElsewhere
                _incomingHandoff.value = _latestSession.value?.takeIf(::isOfferable)
            }
            .onFailure { log("handoff", it) }
    }

    fun dismiss(handoff: AgroHandoffState) {
        dismissed = handoff.key()
        _incomingHandoff.value = null
    }

    fun consume(handoff: AgroHandoffState) = dismiss(handoff)

    suspend fun unregister(): Result<Unit> = sessionApi.unregisterNode()

    internal fun liveUpdates(): Flow<AgroLiveMessage> = webSocket.liveUpdates()

    private fun isOfferable(handoff: AgroHandoffState): Boolean {
        if (handoff.key() == dismissed) return false
        if (devices.value.any { it.deviceId == handoff.deviceId && it.isOnline }) return true
        if (handoff.isRecent()) return true

        if (coldStartOfferAvailable) {
            coldStartOfferAvailable = false
            return true
        }
        return false
    }

    fun allowReoffer() {
        dismissed = null
        _incomingHandoff.value = _latestSession.value?.takeIf(::isOfferable)
    }

    private fun AgroHandoffState.isRecent(): Boolean {
        val updated = runCatching { Instant.parse(updatedAt) }.getOrNull() ?: return false
        return Duration.between(updated, Instant.now()) < MAX_AGE
    }

    private fun AgroHandoffState.key() = "$deviceId|$trackUri"

    private fun log(what: String, error: Throwable) {
        Log.w(TAG, "Agro $what refresh failed: ${error.message}")
    }

    private companion object {
        const val TAG = "AgroSessions"
        val MAX_AGE: Duration = Duration.ofMinutes(10)
    }
}
