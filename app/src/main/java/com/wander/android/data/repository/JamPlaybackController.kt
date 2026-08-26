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
     * True when the room is on a livestream.
     *
     * A stream has no position to be in step *at* — every listener is at its live edge, which is a
     * different moment for each of them and cannot be seeked to. So the room's clock does not
     * apply, and nothing here tries to make it. Everyone hears roughly the same thing, which is
     * the most a broadcast can offer and is plenty for listening together.
     */
    private var playingLive: Boolean = false

    /**
     * Where the room was, and when we heard that — the two numbers the room's position is derived
     * from. Wall-clock *since the frame* rather than the server's `startedAt`, so a phone with a
     * skewed clock still lands in the right place.
     */
    private var basePositionMs: Long = 0L
    private var baseAt: Long = 0L
    private var trackDurationMs: Long = 0L

    private var reconcileJob: Job? = null

    /** When the last corrective seek happened, so the next one can be held off. */
    private var lastCorrectionAt: Long = 0L

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
            playingLive = false
            pending = null
            reconcileJob?.cancel()
            reconcileJob = null
            _outOfSync.value = false
            return
        }
        pending = now
        // Stamped here, before the work below. `follow` has to search for the track and resolve a
        // stream, which takes seconds — measuring the room's clock from *after* that would bake
        // this device's lookup time into where it thinks the room is, permanently and differently
        // on every device.
        val arrivedAt = SystemClock.elapsedRealtime()
        scope.launch {
            lock.withLock {
                val target = pending ?: return@withLock
                pending = null
                follow(target, arrivedAt)
            }
        }
    }

    /** Leaves the room's playback alone from here on. */
    fun reset() {
        reconcileJob?.cancel()
        reconcileJob = null
        playingTrackId = null
        playingLive = false
        pending = null
        _unresolvable.value = null
        _outOfSync.value = false
    }

    private suspend fun follow(now: JamNowPlaying, arrivedAt: Long) {
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
        playingLive = resolved.track.isLive
        basePositionMs = now.positionMs
        baseAt = arrivedAt
        trackDurationMs = now.durationMs
        _outOfSync.value = false
        // Where the room has got to *by now*, not where it was when the frame was sent. Starting
        // at the frame's own position meant beginning however long the lookup took behind the
        // room, and then being seeked forward for it a few seconds later — a correction that was
        // entirely self-inflicted.
        // A stream starts at its live edge. Seeking into one by the room's elapsed time asks for
        // a moment that is not in the window and, on a stream that has been running for hours, is
        // not a moment anyone else is at either.
        val startAt = if (playingLive) 0L else roomPositionMs()
        // A correction immediately after starting would be measuring the buffer, so the cooldown
        // starts here rather than at the first seek.
        lastCorrectionAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Main) {
            playerConnection.playForJam(listOf(resolved.track), startAt)
        }
        // Nothing to reconcile against on a stream, so the loop is not started at all rather than
        // started and made to skip every pass.
        if (!playingLive) startReconciling()
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
                _outOfSync.value = false

                // Buffering is not drift. The position sits still while a buffer fills, so
                // measuring here measures the buffer — and "correcting" it seeks, which starts
                // another buffer, which reads as more drift. That loop is what made this
                // interrupt over and over instead of settling.
                if (playerConnection.state.value.isBuffering) continue

                // A correction is audible, so one is allowed and then the device is left alone
                // for a while. Without this, a phone that simply cannot keep up — a slow stream,
                // a weak signal — was seeked every few seconds forever, and every seek cost it
                // more buffering than the drift it was fixing.
                val sinceLastCorrection = SystemClock.elapsedRealtime() - lastCorrectionAt
                if (sinceLastCorrection < CORRECTION_COOLDOWN_MS) continue

                val actual = withContext(Dispatchers.Main) { playerConnection.currentPositionMs() }
                    ?: continue
                val drift = kotlin.math.abs(actual - expected)
                if (drift > DRIFT_TOLERANCE_MS) {
                    Log.i(TAG, "drifted ${drift}ms from the room; seeking back in step")
                    lastCorrectionAt = SystemClock.elapsedRealtime()
                    withContext(Dispatchers.Main) { playerConnection.followerSeek(expected) }
                }
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
            withContext(Dispatchers.Main) {
                // On a stream there is nowhere to seek back to: rejoining means resuming at the
                // live edge, which is wherever the stream is by the time it starts.
                if (!playingLive) playerConnection.followerSeek(roomPositionMs())
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
         * Two seconds was too tight to be achievable, let alone worth achieving. The room's clock
         * starts when the frame arrives, but this device then has to *search* for the track,
         * resolve a stream and fill a buffer before a sound comes out — seconds, routinely. So a
         * follower is legitimately behind from the moment it starts, and a threshold below that
         * floor guarantees a correction on every pass.
         *
         * Six seconds sits above that floor. Nobody sharing a room is listening on two phones at
         * once, and a few seconds between two people in different places is not something either
         * of them can perceive — whereas the seek that closes it is something both of them hear.
         */
        const val DRIFT_TOLERANCE_MS = 6_000L

        /**
         * How long to leave the device alone after a corrective seek.
         *
         * Correcting costs a rebuffer, which puts the device behind again, which reads as fresh
         * drift. Left ungated that is a loop, and it interrupts playback indefinitely on exactly
         * the connections least able to afford it.
         */
        const val CORRECTION_COOLDOWN_MS = 30_000L
    }
}
