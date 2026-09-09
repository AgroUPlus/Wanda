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
internal class AgroHandoffPublisher @Inject constructor(
    private val agroClient: AgroClient,
    private val handoffApi: AgroHandoffApi,
    private val secureStorage: SecureStorage,
    private val sharedTrackHash: com.wander.android.core.sync.SharedTrackHash,
    private val presenceSealer: PresenceSealer,
    private val friendDao: com.wander.android.core.database.dao.FriendDao
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
    fun publish(
        track: UnifiedTrack,
        positionMs: () -> Long,
        durationMs: () -> Long,
        isPlaying: Boolean
    ) {
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

        // Only a state change re-seals. The heartbeat repeats a position against metadata that
        // has not moved, and sealing that again would be one ciphertext per friend device every
        // thirty seconds to say what the last set already says.
        if (stateChanged) scope.launch { send(track, positionMs, durationMs, isPlaying, seal = true) }
        if (!isPlaying) return

        heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (secureStorage.isIncognitoMode) {
                    stop()
                    break
                }
                send(track, positionMs, durationMs, isPlaying = true, seal = false)
            }
        }
    }

    /**
     * Playback ended or the service is going away: stop claiming to be a live session.
     *
     * The sealed copies go with it. They describe a track that is no longer playing, and the feed
     * that reads them has already stopped showing this session — so what is left on the server is
     * a set of envelopes addressed to friends about something that is over.
     */
    fun stop() {
        heartbeat?.cancel()
        heartbeat = null
        val ended = lastSent
        lastSent = null
        if (ended != null && agroClient.isConfigured) {
            scope.launch { handoffApi.clearPresenceCopies().onFailure { log("clear presence", it) } }
        }
    }

    private suspend fun send(
        track: UnifiedTrack,
        positionMs: () -> Long,
        durationMs: () -> Long,
        isPlaying: Boolean,
        seal: Boolean
    ) {
        if (secureStorage.isIncognitoMode) return
        // Both reads in one hop to the main thread, so the position cannot belong to a different
        // track than the length it is measured against.
        val (position, duration) = withContext(Dispatchers.Main) { positionMs() to durationMs() }
        handoffApi.sendHandoffState(
            trackUri = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            positionMs = position,
            // Media3 answers `TIME_UNSET` until the source is prepared, and a livestream has no
            // length at all. Both become 0, which is the fleet's word for "no bar, just a clock".
            durationMs = duration.coerceAtLeast(0L),
            isPlaying = isPlaying,
            // Computed here if the batch worker has not reached this file yet. Announcing a
            // local file with no hash tells every listener there is nothing to transfer, and all
            // three peer tiers then decline a transfer they have no way to name — which is what
            // made two phones on one Wi-Fi unable to hand each other a song.
            contentHash = sharedTrackHash.of(track.id),
            presenceCopies = if (seal) sealForFriends(track) else null
        ).onFailure { log("handoff", it) }
    }

    /**
     * The session sealed once per friend device, or null when there is nothing to seal to.
     *
     * Null and empty are different instructions to the server — leave the copies alone, versus drop
     * them — so this returns an empty list rather than null when there are simply no friends or no
     * published keys. A session that can no longer be sealed to anyone must clear what the last one
     * left, not inherit it.
     *
     * Only sealed when a vault key is enrolled. Without one the handoff is in clear anyway and
     * friends read the ordinary columns, so a sealed copy would be a second answer to a question
     * already answered.
     */
    private suspend fun sealForFriends(track: UnifiedTrack): List<AgroPresenceCopy>? {
        if (secureStorage.agroVaultKey == null) return null
        val friends = runCatching { friendDao.friendUsernames() }.getOrNull().orEmpty()
        val metadata = handoffApi.sealedMetadata(
            trackUri = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            contentHash = sharedTrackHash.of(track.id)
        ).toString(Charsets.UTF_8)
        return runCatching { presenceSealer.sealFor(friends, metadata) }.getOrElse { emptyList() }
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
