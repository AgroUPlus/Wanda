package com.wander.android.data.sources.agro

import android.util.Log
import com.wander.android.data.model.UnifiedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.wander.android.core.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes what this device is playing to a paired Agro server, so another client can pick the
 * track up where it left off — and so this device shows up as a live session at all.
 *
 * Two kinds of send, because the server needs both:
 *
 * - **Events**: track transitions and play/pause, deduped so one user action is one request.
 * - **A heartbeat while playing**: the server marks a node online only if it heard from it in the
 *   last 45 seconds (`active_nodes` in the Agro schema), and `updateHandoff` is what refreshes
 *   that. Reporting on events alone meant a four-minute track went by in silence and the phone
 *   dropped off the device list mid-song.
 *
 * The heartbeat exists **only while audio is playing** — it starts on a playing state and is
 * cancelled the moment playback stops, so an idle or paused app sends nothing at all.
 */
@Singleton
class AgroHandoffPublisher @Inject constructor(
    private val agroClient: AgroClient,
    private val secureStorage: SecureStorage
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastSent: Handoff? = null
    private var heartbeat: Job? = null

    /** One-shot, on app start: tells the server this device exists before anything plays. */
    fun register() {
        if (!agroClient.isConfigured) return
        scope.launch {
            agroClient.registerNode().onFailure { log("register", it) }
        }
    }

    /**
     * [positionMs] is a provider rather than a value because the heartbeat re-reads it on every
     * tick. Media3 requires that read on the application thread, so it happens on [Dispatchers.Main]
     * while the request itself stays on IO.
     */
    fun publish(track: UnifiedTrack, positionMs: () -> Long, isPlaying: Boolean) {
        if (!agroClient.isConfigured) return

        heartbeat?.cancel()
        heartbeat = null

        // Incognito was suppressing scrobbles and play counts while still telling the fleet — and
        // through it, every friend — exactly what was playing. Publishing presence is the most
        // visible thing the app does with a listen, so it has to be the first thing incognito
        // stops. Checked *after* the heartbeat is cancelled, so switching incognito on mid-track
        // silences the running heartbeat at the next state change rather than leaving it ticking.
        if (secureStorage.isIncognitoMode) return

        val handoff = Handoff(track.id, isPlaying)
        val stateChanged = handoff != lastSent
        lastSent = handoff

        if (stateChanged) scope.launch { send(track, positionMs, isPlaying) }
        if (!isPlaying) return

        heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                send(track, positionMs, isPlaying = true)
            }
        }
    }

    /** Playback ended or the service is going away: stop claiming to be a live session. */
    fun stop() {
        heartbeat?.cancel()
        heartbeat = null
        lastSent = null
    }

    private suspend fun send(track: UnifiedTrack, positionMs: () -> Long, isPlaying: Boolean) {
        val position = withContext(Dispatchers.Main) { positionMs() }
        agroClient.sendHandoffState(
            trackUri = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            positionMs = position,
            isPlaying = isPlaying
        ).onFailure { log("handoff", it) }
    }

    /** Only the failure text — never the server URL or key, both of which are credentials. */
    private fun log(what: String, error: Throwable) {
        Log.w(TAG, "Agro $what failed: ${error.message}")
    }

    /** Position is deliberately excluded: it changes constantly and must not trigger a send. */
    private data class Handoff(val trackId: String, val isPlaying: Boolean)

    private companion object {
        const val TAG = "AgroHandoff"

        /** Comfortably inside the server's 45s online window, without being chatty. */
        const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
