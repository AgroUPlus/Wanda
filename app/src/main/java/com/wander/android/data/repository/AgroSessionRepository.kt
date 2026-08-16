package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.sources.agro.AgroGraphQl
import com.wander.android.data.sources.agro.AgroHandoffState
import com.wander.android.data.sources.agro.AgroLiveMessage
import com.wander.android.data.sources.agro.AgroNode
import com.wander.android.data.sources.agro.AgroSessionApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val graphQl: AgroGraphQl,
    private val connectivity: ConnectivityObserver
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

    /**
     * One "pick up where you left off" per app process, however stale the session is.
     *
     * Without it the only sessions ever offered were live ones or ones minutes old — so closing the
     * desktop player and opening the phone an hour later, which is the single most obvious thing to
     * want, offered nothing at all. It is spent the first time it produces an offer rather than on
     * dismissal, so it cannot come back on every foreground.
     */
    private var coldStartOfferAvailable = true

    suspend fun refresh() {
        // Offline the server is simply unreachable; a round of requests that can only time out is
        // worse than no round at all. The cached device list and session stay as they were.
        if (!connectivity.isOnline.value) return

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
                // The server holds one session per user, so this device publishing its own
                // playback *replaces* the other device's entry. That is not the other session
                // ending, and treating it as such is what made the session vanish for a few
                // seconds — then come back, offer and all, the moment the other device sent its
                // next update. Only a genuinely empty server session clears it.
                if (fromElsewhere != null || handoff == null) _latestSession.value = fromElsewhere
                _incomingHandoff.value = _latestSession.value?.takeIf(::isOfferable)
            }
            .onFailure { log("handoff", it) }
    }

    fun dismiss(handoff: AgroHandoffState) {
        dismissed = handoff.key()
        _incomingHandoff.value = null
    }

    /** Once resumed, this device owns the session — offering it back would be a loop. */
    fun consume(handoff: AgroHandoffState) = dismiss(handoff)

    /** Tells Agro to remove this device registration so it is not duplicated. */
    suspend fun unregister(): Result<Unit> = sessionApi.unregisterNode()

    /**
     * Live updates while the UI is foreground.
     *
     * A message is only ever a hint to re-query, never trusted as state. The socket closes as soon
     * as collection stops, so nothing is held open in the background.
     *
     * Reconnects with a capped backoff. A dropped socket used to end the flow for good, so one
     * suspend or one server restart left the app silently poll-only until it was next foregrounded
     * — which looked exactly like sync being broken.
     *
     * With no network there is nothing to back off *to*, so the retry parks on connectivity instead
     * of burning the radio on a handshake that cannot complete. It resumes on the next online edge.
     */
    fun liveUpdates(): Flow<AgroLiveMessage> = connectOnce().retryWhen { _, attempt ->
        connectivity.isOnline.first { it }
        val backoff = (BASE_BACKOFF_MS shl attempt.coerceAtMost(5).toInt())
            .coerceAtMost(MAX_BACKOFF_MS)
        delay(backoff)
        true
    }

    /** One connection's lifetime. Fails rather than completes, so [liveUpdates] can retry it. */
    private fun connectOnce(): Flow<AgroLiveMessage> = callbackFlow {
        val url = graphQl.syncSocketUrl().takeIf { connectivity.isOnline.value }
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
                    parse(text)?.let { trySend(it) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Closed with the cause rather than cleanly, so the retry above sees a failure
                    // and reconnects. Still not surfaced to the user: a dropped socket is not
                    // something they can act on, and polling covers the gap.
                    Log.w(TAG, "Agro live updates stopped: ${t.message}")
                    close(t)
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
        if (handoff.isRecent()) return true

        // See [coldStartOfferAvailable]. Spent here, where the offer is actually made.
        if (coldStartOfferAvailable) {
            coldStartOfferAvailable = false
            return true
        }
        return false
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

    /**
     * Deliberately device and track only. `updatedAt` used to be part of it, which meant every
     * heartbeat and every pause/resume on the other device produced a "new" session that walked
     * straight past the dismissal — the same card, re-offered seconds after it was declined.
     */
    private fun AgroHandoffState.key() = "$deviceId|$trackUri"

    private fun log(what: String, error: Throwable) {
        Log.w(TAG, "Agro $what refresh failed: ${error.message}")
    }

    /**
     * Reads one frame, or nothing.
     *
     * Parsed as JSON rather than substring-matched. The old check asked whether "HANDOFF" appeared
     * anywhere in the text, which both missed the library messages entirely and would have matched
     * a track called "HANDOFF" in someone's album title.
     */
    private fun parse(text: String): AgroLiveMessage? {
        val envelope = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null
        return when (envelope["msg_type"]?.jsonPrimitive?.contentOrNull) {
            "HANDOFF", "NODE_UPDATE" -> AgroLiveMessage.Session
            "SYNC_OFFER", "LIBRARY_UPDATED" -> {
                val payload = envelope["payload"] as? JsonObject
                AgroLiveMessage.Library(
                    newTrackCount = payload?.get("count")?.jsonPrimitive?.intOrNull ?: 0,
                    albums = payload?.get("albums")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        .orEmpty()
                )
            }
            // SETTINGS_SYNC and anything a newer server adds are ignored by name rather than by
            // accident, so adding one later is a single branch.
            else -> null
        }
    }

    private companion object {
        const val TAG = "AgroSessions"
        const val NORMAL_CLOSURE = 1000
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        val MAX_AGE: Duration = Duration.ofMinutes(10)
    }
}
