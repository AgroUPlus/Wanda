package com.wander.android.data.repository

import android.os.SystemClock
import android.util.Log
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.sources.agro.JamNowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays whatever the server says the room is hearing.
 *
 * A pure follower, and deliberately so. Two earlier versions let a device decide for itself when a
 * track was over: the first made whoever opened the jam a DJ, and the second had every device
 * advance independently — which, through a comparison of the jam's server uuid against the local
 * track's id, retired the entire queue in one pass and played only the last song.
 *
 * So nothing here decides anything. The server holds one track and one start time, pushes it, and
 * this puts the needle down at the offset it was given. A device that pauses, stalls or cannot find
 * the track simply falls behind and is put back in step by the next frame.
 */
@Singleton
internal class JamPlaybackController @Inject constructor(
    private val resolver: ListenAlongResolver,
    private val playerConnection: PlayerConnection
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Frames are applied one at a time, newest only.
     *
     * Resolving a track is a search, so two frames arriving close together would otherwise race and
     * the older one could land last — the same fault that made listen-along replay the previous
     * song on a skip.
     */
    private val lock = Mutex()
    private var pending: JamNowPlaying? = null

    /** The jam track currently mirrored, so an unchanged frame does not restart it. */
    private var playingTrackId: String? = null

    /**
     * Where the room was, and when we heard that — the two numbers the room's position is derived
     * from. Wall-clock *since the frame* rather than the server's `startedAt`, so a phone with a
     * skewed clock still lands in the right place.
     */
    private var basePositionMs: Long = 0L
    private var baseAt: Long = 0L
    private var trackDurationMs: Long = 0L

    private var reconcileJob: Job? = null

    private val _unresolvable = MutableStateFlow<String?>(null)
    /** A track the room is playing that no source here has. */
    val unresolvable: StateFlow<String?> = _unresolvable.asStateFlow()

    private val _outOfSync = MutableStateFlow(false)
    /**
     * True when this device is playing the right track but is no longer with the room — paused, or
     * dragged somewhere else on the seek bar.
     *
     * Surfaced rather than silently corrected. Nothing here can stop somebody pausing their own
     * phone: the device owns its audio, and a follower that force-resumed would be fighting its
     * user rather than following a room. Drift *while playing* is corrected silently, because that
     * is a fault rather than a decision; a pause is a decision, so it is reported and the user is
     * offered a way back in.
     */
    val outOfSync: StateFlow<Boolean> = _outOfSync.asStateFlow()

    /** One `JAM_NOW_PLAYING` frame. */
    fun onNowPlaying(now: JamNowPlaying?) {
        if (now == null) {
            // Between tracks, or the queue ran dry. What is playing keeps playing: cutting the
            // audio dead would be a worse surprise than a moment of the previous song.
            playingTrackId = null
            pending = null
            reconcileJob?.cancel()
            reconcileJob = null
            _outOfSync.value = false
            return
        }
        pending = now
        scope.launch {
            lock.withLock {
                val target = pending ?: return@withLock
                pending = null
                follow(target)
            }
        }
    }

    /** Leaves the room's playback alone from here on. */
    fun reset() {
        reconcileJob?.cancel()
        reconcileJob = null
        playingTrackId = null
        pending = null
        _unresolvable.value = null
        _outOfSync.value = false
    }

    private suspend fun follow(now: JamNowPlaying) {
        // The same track again — a re-announcement, or a second listener joining. Already playing.
        if (now.trackId == playingTrackId) return

        val resolved = resolver.resolve(now.title, now.artist)
        if (resolved == null) {
            Log.i(TAG, "no source here has \"${now.title}\"")
            _unresolvable.value = now.title
            // Nothing is skipped. The room is still playing it, and skipping ahead is exactly what
            // put a device out of step with everyone else before.
            playingTrackId = null
            return
        }

        _unresolvable.value = null
        playingTrackId = now.trackId
        basePositionMs = now.positionMs
        baseAt = SystemClock.elapsedRealtime()
        trackDurationMs = now.durationMs
        _outOfSync.value = false
        withContext(Dispatchers.Main) {
            // Started at the room's position, not at zero, so joining late lands in the right place.
            playerConnection.playForJam(listOf(resolved.track), now.positionMs)
        }
        startReconciling()
    }

    /** Where the room is now, derived from the last frame and the time since it arrived. */
    private fun roomPositionMs(): Long = basePositionMs + (SystemClock.elapsedRealtime() - baseAt)

    /**
     * Keeps this device level with the room *within* a track.
     *
     * Without this the only correction was the next `JAM_NOW_PLAYING` frame, so a device that
     * stalled for ten seconds on a slow buffer stayed ten seconds behind everybody for the rest of
     * the song. The room's position is authoritative; this only ever moves the local player toward
     * it, never the other way.
     */
    private fun startReconciling() {
        reconcileJob?.cancel()
        reconcileJob = scope.launch {
            while (true) {
                delay(RECONCILE_INTERVAL_MS)
                val expected = roomPositionMs()
                // Past the end: the server is about to move the room on, and seeking into the last
                // half-second of a track just to be corrected again is churn.
                if (playingTrackId == null) continue
                if (trackDurationMs > 0 && expected >= trackDurationMs - RECONCILE_INTERVAL_MS) continue

                val playing = withContext(Dispatchers.Main) { playerConnection.isPlayingNow() }
                if (!playing) {
                    // Paused by the user. Left alone deliberately — see [outOfSync].
                    _outOfSync.value = true
                    continue
                }
                val actual = withContext(Dispatchers.Main) { playerConnection.currentPositionMs() }
                    ?: continue
                val drift = kotlin.math.abs(actual - expected)
                if (drift > DRIFT_TOLERANCE_MS) {
                    Log.i(TAG, "drifted ${drift}ms from the room; seeking back in step")
                    withContext(Dispatchers.Main) { playerConnection.followerSeek(expected) }
                }
                _outOfSync.value = false
            }
        }
    }

    /**
     * Puts this device back where the room is, now.
     *
     * Snaps to the room's *current* position rather than resuming where the user left off, which is
     * the whole difference between rejoining a room and replaying part of it.
     */
    fun resync() {
        if (playingTrackId == null) return
        scope.launch {
            val expected = roomPositionMs()
            withContext(Dispatchers.Main) {
                playerConnection.followerSeek(expected)
                playerConnection.followerSetPlaying(true)
            }
            _outOfSync.value = false
        }
    }

    private companion object {
        const val TAG = "JamPlayback"

        /** How often to check. Often enough to catch a stall, rare enough to be free. */
        const val RECONCILE_INTERVAL_MS = 5_000L

        /**
         * How far out of step is worth correcting.
         *
         * Two seconds rather than something tighter: a seek is audible, and correcting a drift
         * nobody can hear would make the room stutter in the name of a synchronicity it already
         * had. Network jitter and buffering routinely account for a second either way.
         */
        const val DRIFT_TOLERANCE_MS = 2_000L
    }
}
