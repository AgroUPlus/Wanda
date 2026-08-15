package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.sources.agro.AgroGraphQl
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroNode
import com.wander.android.data.sources.agro.AgroSessionApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the rest of the fleet is doing: the other registered devices, and the one session the
 * server holds per user.
 *
 * Reads only. Publishing this device's own state stays in `AgroHandoffPublisher`, which runs in
 * the playback service so it survives with no UI attached.
 */
@Singleton
class AgroSessionRepository @Inject constructor(
    private val sessionApi: AgroSessionApi,
    private val graphQl: AgroGraphQl
) {
    private val _devices = MutableStateFlow<List<AgroNode>>(emptyList())
    val devices: StateFlow<List<AgroNode>> = _devices.asStateFlow()

    /**
     * The auto-offer: gated so a card only interrupts when it is genuinely useful — another
     * device, playing, recently. See [isOfferable].
     */
    private val _incomingHandoff = MutableStateFlow<AgroHandoffState?>(null)
    val incomingHandoff: StateFlow<AgroHandoffState?> = _incomingHandoff.asStateFlow()

    /**
     * The last session from any other device, with none of that gating. Dismissing the card, or a
     * session going quiet, must not make it unresumable — this is what the sessions list offers,
     * and it is available for as long as the server remembers it.
     */
    private val _latestSession = MutableStateFlow<AgroHandoffState?>(null)
    val latestSession: StateFlow<AgroHandoffState?> = _latestSession.asStateFlow()

    /** In memory only: a session declined now should be offerable again next launch. */
    private var dismissed: String? = null

    suspend fun refresh() {
        if (!graphQl.isConfigured) {
            _devices.value = emptyList()
            _incomingHandoff.value = null
            _latestSession.value = null
            return
        }

        sessionApi.activeNodes().onSuccess { nodes ->
            // This device is not a device you can hand off *to*.
            _devices.value = nodes
                .filterNot { it.deviceId == graphQl.deviceId }
                .sortedByDescending { it.isOnline }
        }.onFailure { log("devices", it) }

        sessionApi.playbackHandoff()
            .onSuccess { handoff ->
                val fromElsewhere = handoff?.takeIf {
                    it.deviceId != graphQl.deviceId && it.trackTitle.isNotBlank()
                }
                _latestSession.value = fromElsewhere
                _incomingHandoff.value = fromElsewhere?.takeIf(::isOfferable)
            }
            .onFailure { log("handoff", it) }
    }

    fun dismiss(handoff: AgroHandoffState) {
        dismissed = handoff.key()
        _incomingHandoff.value = null
    }

    /** Once resumed, this device owns the session — offering it back would be a loop. */
    fun consume(handoff: AgroHandoffState) = dismiss(handoff)

    /**
     * Live updates while the UI is foreground. The server broadcasts to every subscriber with no
     * per-user filtering, so a message is only ever a hint to re-query — never trusted as state.
     * The socket closes as soon as collection stops, so nothing is held open in the background.
     */
    fun liveUpdates(): Flow<Unit> = callbackFlow {
        val url = graphQl.syncSocketUrl()
        if (url == null) {
            close()
            return@callbackFlow
        }
        // The socket needs the same credential as the API: without it the server closes the
        // handshake with 401 and live updates silently never arrive, leaving the app looking like
        // it only refreshes when a screen happens to ask.
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${graphQl.apiKey}")
            .build()
        val socket = HttpClientFactory.okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (RELEVANT.any { it in text }) trySend(Unit)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Losing the socket is not an error worth surfacing: the one-shot refresh on
                    // resume already covers it, and reconnect storms cost battery.
                    Log.w(TAG, "Agro live updates stopped: ${t.message}")
                    close()
                }
            }
        )
        awaitClose { socket.close(NORMAL_CLOSURE, null) }
    }

    /**
     * Only decides whether to *offer* unprompted. [latestSession] stays resumable regardless.
     *
     * A paused session counts. Pausing on the desktop and picking the phone up is the exact moment
     * this feature is for, and requiring `isPlaying` meant that moment was the one case that never
     * produced an offer.
     */
    private fun isOfferable(handoff: AgroHandoffState): Boolean {
        if (handoff.key() == dismissed) return false
        // A device that is *still online* is still worth offering, however long it has been
        // playing. Age alone used to decide, so a session running for more than [MAX_AGE] stopped
        // being offered precisely because it was going strong — and only publishes on track
        // changes, so a long album could go quiet for the whole of it.
        if (devices.value.any { it.deviceId == handoff.deviceId && it.isOnline }) return true
        return handoff.isRecent()
    }

    /**
     * Makes a previously dismissed session offerable again.
     *
     * Called when the app comes to the foreground with nothing playing here: at that moment the
     * offer is useful again rather than an interruption, which is the whole point of it. A
     * dismissal still stands for as long as the user stays in the app.
     */
    fun allowReoffer() {
        dismissed = null
        _incomingHandoff.value = _latestSession.value?.takeIf(::isOfferable)
    }

    /**
     * A day-old session from a device that is now offline is a curiosity, not an offer.
     * `updatedAt` is RFC3339 from the server; an unparseable value is treated as stale rather
     * than assumed fresh.
     */
    private fun AgroHandoffState.isRecent(): Boolean {
        val updated = runCatching { Instant.parse(updatedAt) }.getOrNull() ?: return false
        return Duration.between(updated, Instant.now()) < MAX_AGE
    }

    private fun AgroHandoffState.key() = "$deviceId|$trackUri|$updatedAt"

    private fun log(what: String, error: Throwable) {
        Log.w(TAG, "Agro $what refresh failed: ${error.message}")
    }

    private companion object {
        const val TAG = "AgroSessions"
        const val NORMAL_CLOSURE = 1000
        val MAX_AGE: Duration = Duration.ofMinutes(10)
        val RELEVANT = listOf("HANDOFF", "NODE_UPDATE")
    }
}
