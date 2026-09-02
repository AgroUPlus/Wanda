package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroListenAlongApi
import com.wander.android.data.sources.agro.AgroLiveMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A live listen-along, as the UI needs to describe it. */
internal data class ListenAlongSession(
    val host: String,
    val listenerCount: Int,
    val nowPlaying: AgroFriendNowPlaying?,
    val resolvedFrom: ResolvedFrom?,
    /** Set when the host is playing something this device cannot find anywhere. */
    val unresolvable: String? = null
)

/**
 * Follows a friend's playback.
 *
 * The server pushes a `LISTEN_ALONG` frame on every track change, which this turns into playback
 * here. Three things make that harder than it sounds, and each is handled explicitly rather than
 * hopefully:
 *
 * - **The host's track id is meaningless here.** It names a row in their Navidrome or a video in
 *   their YouTube session. [ListenAlongResolver] matches by name instead, and reports which of your
 *   sources answered, because a name match is a guess and the listener should see that it was one.
 * - **Position drifts.** Seeking on every frame would stutter through the whole song, so the player
 *   is only pulled back into line once it has drifted past [DRIFT_TOLERANCE_MS].
 * - **What plays is not your choice.** [ScrobbleSuppression] keeps it out of your history for as
 *   long as the session lasts.
 */
@Singleton
internal class ListenAlongController @Inject constructor(
    private val api: AgroListenAlongApi,
    private val resolver: ListenAlongResolver,
    private val playerConnection: PlayerConnection,
    private val suppression: ScrobbleSuppression,
    private val musicRepository: MusicRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _session = MutableStateFlow<ListenAlongSession?>(null)
    val session: StateFlow<ListenAlongSession?> = _session.asStateFlow()

    /** The track currently mirrored, so an unchanged frame does not restart it. */
    private var playingKey: String? = null

    /**
     * Frames are applied one at a time, and only the newest is applied.
     *
     * Each frame used to start its own coroutine. Following a track is not instant — it searches
     * for a match, then hands it to the player — so two frames arriving close together ran
     * concurrently and finished in whatever order the searches happened to complete. When the
     * older one finished last it put the *previous* song back, which is exactly what a skip looks
     * like from here.
     *
     * The lock serialises them; [pending] holds only the most recent, so a frame overtaken while
     * waiting is dropped rather than played late.
     */
    private val followMutex = Mutex()
    private var pending: AgroFriendNowPlaying? = null

    suspend fun start(host: String): Result<Unit> = api.startListenAlong(host).map { state ->
        suppression.set(true)
        // The transport becomes the host's for the duration. Starting different music here ends
        // the session instead of being refused — see PlayerConnection.play.
        playerConnection.setFollowing(true) { scope.launch { stop() } }
        _session.value = ListenAlongSession(
            host = state.host,
            listenerCount = state.listeners.size,
            nowPlaying = state.nowPlaying,
            resolvedFrom = null
        )
        playingKey = null
        state.nowPlaying?.let { now ->
            pending = now
            scope.launch {
                followMutex.withLock {
                    val target = pending ?: return@withLock
                    pending = null
                    follow(target)
                }
            }
        }
        Unit
    }

    suspend fun stop(): Result<Unit> {
        // Cleared first: whatever the server says, this device stops attributing a friend's
        // listening to its owner the moment they ask it to.
        suppression.set(false)
        playerConnection.setFollowing(false)
        _session.value = null
        playingKey = null
        pending = null
        // The URLs resolved for this session name a private address and carry a bearer token. They
        // are worth exactly as long as the session was.
        musicRepository.clearEphemeralStreams()
        return api.stopListenAlong().map { }
    }

    /**
     * Handles one socket frame.
     *
     * Frames naming a host we are not following are ignored. The same message type also tells a
     * host about their own listeners, and acting on those would make two friends following each
     * other chase one another's position indefinitely.
     */
    fun onFrame(frame: AgroLiveMessage.ListenAlong) {
        val current = _session.value ?: return
        if (!frame.host.equals(current.host, ignoreCase = true)) return

        if (frame.stopped) {
            // The host closed their now-playing switch, or the friendship ended. The music keeps
            // playing — cutting the audio dead would be a worse surprise than merely stopping
            // following.
            scope.launch { stop() }
            return
        }
        if (frame.trackTitle.isBlank()) return

        pending = AgroFriendNowPlaying(
            username = frame.host,
            trackUri = frame.trackUri,
            trackTitle = frame.trackTitle,
            artistName = frame.artistName,
            albumName = frame.albumName,
            artworkUrl = frame.artworkUrl,
            positionMs = frame.positionMs,
            isPlaying = frame.isPlaying,
            updatedAt = "",
            deviceId = frame.deviceId,
            contentHash = frame.contentHash,
            peerLanAddress = frame.peerLanAddress,
            peerLanToken = frame.peerLanToken
        )
        scope.launch {
            followMutex.withLock {
                // Whatever is newest at the moment the lock is taken, or nothing if an earlier
                // waiter already applied it.
                val target = pending ?: return@withLock
                pending = null
                follow(target)
            }
        }
    }

    private suspend fun follow(now: AgroFriendNowPlaying) {
        _session.value = _session.value?.copy(nowPlaying = now)

        val key = now.artistName + " " + now.trackTitle
        if (key == playingKey) {
            correctDrift(now)
            matchTransport(now)
            return
        }

        val resolved = resolver.resolve(
            title = now.trackTitle,
            artist = now.artistName,
            hostDevice = now.deviceId,
            hostLanAddress = now.peerLanAddress,
            hostLanToken = now.peerLanToken,
            contentHash = now.contentHash
        )
        if (resolved == null) {
            // No fallback and no placeholder. Naming the track that could not be found is the only
            // honest thing left, and it is more useful than silence.
            Log.i(TAG, "No source has that track")
            _session.value = _session.value?.copy(
                resolvedFrom = null,
                unresolvable = now.trackTitle
            )
            playingKey = null
            return
        }

        playingKey = key
        _session.value = _session.value?.copy(resolvedFrom = resolved.from, unresolvable = null)
        // Media3 must be touched from the main thread.
        withContext(Dispatchers.Main) {
            // Starting the mirrored track must not read as the user choosing something else, so
            // the flag is lowered for exactly this call and raised again immediately.
            playerConnection.setFollowing(false)
            playerConnection.play(listOf(resolved.track), startPositionMs = now.positionMs)
            playerConnection.setFollowing(true) { scope.launch { stop() } }
            playerConnection.followerSetPlaying(now.isPlaying)
        }
    }

    /**
     * Nudges the player back to the host's position, but only once it has genuinely fallen behind.
     *
     * Every frame carries a position, and seeking to each one would restart the decoder several
     * times a minute to fix a discrepancy nobody can hear.
     */
    private suspend fun correctDrift(now: AgroFriendNowPlaying) = withContext(Dispatchers.Main) {
        // Buffering is not drift. The position stands still while a buffer fills, so measuring here
        // measures the buffer — and "correcting" it seeks, which starts another buffer, which reads
        // as more drift. `JamPlaybackController` learned this and guards for it; this path never
        // did, which is why the same stutter appeared only when following a friend.
        if (playerConnection.state.value.isBuffering) return@withContext
        // Read off the controller rather than `state`, which deliberately carries no position —
        // the UI gets that from a ticker that only runs while it is on screen.
        val here = playerConnection.controller.value?.currentPosition ?: return@withContext
        if (kotlin.math.abs(here - now.positionMs) > DRIFT_TOLERANCE_MS) {
            // The follower path: ordinary `seekTo` is inert while following, by design.
            playerConnection.followerSeek(now.positionMs)
        }
    }

    /**
     * Pauses when they pause, resumes when they resume.
     *
     * Position was followed but play/pause was not, so a host pausing left this device playing on
     * alone — which is the one moment the two are most obviously out of step. Compared against the
     * player's real state rather than tracked here, so a local pause is respected until the host
     * next changes something.
     */
    private suspend fun matchTransport(now: AgroFriendNowPlaying) = withContext(Dispatchers.Main) {
        playerConnection.followerSetPlaying(now.isPlaying)
    }

    private companion object {
        const val TAG = "ListenAlong"

        /**
         * How far out of step is tolerated. Two seconds is well past what anyone notices when they
         * are not listening on the same speakers, and comfortably above ordinary network jitter.
         */
        const val DRIFT_TOLERANCE_MS = 2_000L
    }
}
