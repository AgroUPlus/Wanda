package com.wander.android.core.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.UnifiedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * The UI's handle on playback: a [MediaController] bound to [PlaybackService], exposed as flows.
 *
 * There is no polling loop here. State changes arrive as player callbacks; the playback position
 * is a separate flow that only ticks while something is actually playing and the screen is on
 * (see `rememberPlaybackPosition`).
 */
@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    /**
     * Endless-radio top-up. Owned by [SecureStorage] rather than held here, because a flag that
     * only lived in this singleton was gone the moment the process was.
     */
    val isRadioMode: StateFlow<Boolean> get() = secureStorage.isRadioMode

    /**
     * Playback failures, phrased for the user.
     *
     * Without this a failed stream resolve died inside ExoPlayer and the UI simply sat there: no
     * message, no log, indistinguishable from a track that had not started yet. Anything that
     * stops playback has to say so.
     */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /**
     * Things worth telling the user that are not failures.
     *
     * Separate from [errors] so a confirmation is not dressed up as a problem. Used by the
     * controls whose whole effect is invisible until the queue happens to run out — radio mode
     * being the one that prompted it.
     */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    // Fast track lookup cache to avoid repeatedly deserializing JSON on the UI thread
    private val trackCache = java.util.concurrent.ConcurrentHashMap<String, UnifiedTrack>()
    private var lastQueue: List<UnifiedTrack> = emptyList()

    val state: StateFlow<PlaybackState> = _controller
        .flatMapLatest { ctrl ->
            if (ctrl == null) flowOf(PlaybackState()) else callbackFlow {
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        val timelineChanged = events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                            lastQueue.size != player.mediaItemCount
                        if (timelineChanged) {
                            lastQueue = player.queueTracks(trackCache)
                        }
                        trySend(player.buildSnapshot(secureStorage.isRadioMode.value, lastQueue, trackCache))
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (retryContainerMismatch(ctrl, error)) return
                        if (rejoinLiveEdge(ctrl)) return
                        _errors.tryEmit(error.userMessage())
                    }
                }
                ctrl.addListener(listener)
                lastQueue = ctrl.queueTracks(trackCache)
                trySend(ctrl.buildSnapshot(secureStorage.isRadioMode.value, lastQueue, trackCache))
                awaitClose { ctrl.removeListener(listener) }
            }
        }
        .combine(secureStorage.isRadioMode) { state, radio -> state.copy(isRadioMode = radio) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PlaybackState())

    /**
     * Ids already retried as a livestream, so a genuinely unplayable file cannot loop forever.
     */
    private val retriedAsLive = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    /**
     * Re-prepares the current item after it failed to parse as a media container (progressive vs HLS).
     *
     * The stream URL is hidden behind a `wanda://track/…` placeholder until load time, so the
     * media-source factory has to choose progressive or HLS from a MIME hint set when the item was
     * *queued* — before anything has fetched it. When YouTube hands back an HLS manifest for a track
     * queued as progressive, or a direct audio format for a live stream marked as HLS, the extractors
     * report that they cannot parse the container.
     *
     * In either case, the item is swapped to the opposite container hint and prepared again. Once
     * per id, so a genuinely corrupt file still surfaces as an error instead of looping forever.
     */
    private fun retryContainerMismatch(ctrl: MediaController, error: PlaybackException): Boolean {
        if (error.errorCode !in CONTAINER_PARSE_ERRORS) return false
        val item = runCatching { ctrl.currentMediaItem }.getOrNull() ?: return false
        val id = item.mediaId.takeIf { it.isNotBlank() } ?: return false

        val uri = item.localConfiguration?.uri
        if (uri != null && uri.scheme != WANDA_SCHEME) return false

        if (!retriedAsLive.add(id)) return false

        val index = runCatching { ctrl.currentMediaItemIndex }.getOrNull() ?: return false
        val isHls = uri?.toString()?.endsWith(LIVE_SUFFIX) == true ||
            item.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8

        val newUri = if (isHls) {
            Uri.parse("$WANDA_SCHEME://track/${Uri.encode(id)}")
        } else {
            Uri.parse("$WANDA_SCHEME://track/${Uri.encode(id)}$LIVE_SUFFIX")
        }

        val builder = item.buildUpon().setUri(newUri)
        if (isHls) {
            builder.setMimeType(null)
        } else {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            // A YouTube item that turns out to be a manifest is a livestream — the badges just
            // failed to say so. Without the live configuration the player reads the manifest's
            // window as the item's duration, plays to the end of it and advances the queue, and
            // the playlist tracker eventually gives up with PlaylistStuckException.
            builder.setLiveConfiguration(liveConfiguration())
        }

        ctrl.replaceMediaItem(index, builder.build())
        ctrl.prepare()
        ctrl.play()
        return true
    }

    /**
     * Puts a livestream back on the air after a load error, instead of reporting one.
     *
     * For a broadcast, "the segment you asked for is gone" is not a failure worth telling the user
     * about — it means the player fell behind the window, which a pause, a tunnel or a moment of
     * bad signal is enough to do. The answer is the same one a radio gives: rejoin at whatever is
     * playing now. Only an error the user can act on should reach them.
     *
     * Bounded per item, because a stream that has genuinely ended would otherwise be re-prepared
     * forever, and "this station is off the air" is something they *do* need to be told.
     */
    private fun rejoinLiveEdge(ctrl: MediaController): Boolean {
        if (state.value.currentTrack?.isLive != true) return false
        val id = runCatching { ctrl.currentMediaItem?.mediaId }.getOrNull() ?: return false
        if (!liveRejoins.allow(id)) return false

        ctrl.seekToDefaultPosition()
        ctrl.prepare()
        ctrl.play()
        return true
    }

    private val liveRejoins = LiveRejoinBudget()

    /** A play request that arrived before the controller existed. See [play]. */
    private data class PendingPlay(
        val tracks: List<UnifiedTrack>,
        val startIndex: Int,
        val startPositionMs: Long
    )

    private var pendingPlay: PendingPlay? = null

    /** Connects to the service. Idempotent; safe to call from `Activity.onStart`. */
    fun connect() {
        if (_controller.value != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                _controller.value = runCatching { future.get() }.getOrNull()
                pendingPlay?.let { queued ->
                    pendingPlay = null
                    play(queued.tracks, queued.startIndex, queued.startPositionMs)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun release() {
        _controller.value?.release()
        _controller.value = null
        trackCache.clear()
        lastQueue = emptyList()
    }

    // ── Commands ────────────────────────────────────────────────────────────────────────────

    /**
     * [startPositionMs] is handed to Media3 with the queue rather than seeked to afterwards —
     * a `seekTo` after `prepare()` races the initial buffer and can start the track from zero.
     * Resuming another device's session (see `AgroSessionRepository`) is what needs it.
     */
    fun play(tracks: List<UnifiedTrack>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        // In a jam, choosing a track proposes it to the room instead of playing it here. That is
        // the whole point of a shared queue: one person deciding what plays by pressing play is
        // the thing voting exists to replace.
        onPlayInJam?.let { propose ->
            // The *tapped* track, not the head of the list. Callers hand over the whole row and say
            // which one was chosen — `play(section.tracks, index)` — so proposing `tracks.first()`
            // silently suggested the row's opening track whatever you actually pressed.
            propose(tracks, startIndex.coerceIn(0, tracks.lastIndex))
            return
        }
        // Deliberately *not* gated on `isFollowing`. Picking something else to play is an
        // unambiguous decision, and answering it with a dialog every time — "leave the session?" —
        // is worse than simply doing what was asked. `onLeaveFollowing` ends the session quietly;
        // the banner disappearing is the confirmation.
        if (isFollowing) onLeaveFollowing?.invoke()
        val ctrl = _controller.value ?: run {
            // Resuming a session can be the first thing that happens after a cold start, before
            // the controller has finished binding. Dropping the request there is what made resume
            // look like it did nothing at all; instead, connect and replay it once bound.
            pendingPlay = PendingPlay(tracks, startIndex, startPositionMs)
            connect()
            return
        }
        tracks.forEach { trackCache[it.id] = it }
        ctrl.setMediaItems(tracks.map(UnifiedTrack::toMediaItem), startIndex, startPositionMs)
        ctrl.prepare()
        ctrl.play()
    }

    fun addToQueue(tracks: List<UnifiedTrack>) {
        val ctrl = _controller.value ?: return
        tracks.forEach { trackCache[it.id] = it }
        ctrl.addMediaItems(tracks.map(UnifiedTrack::toMediaItem))
    }

    /** Inserts right after the current track, so it plays when this one ends. */
    fun playNext(tracks: List<UnifiedTrack>) {
        val ctrl = _controller.value ?: return
        if (tracks.isEmpty()) return
        tracks.forEach { trackCache[it.id] = it }
        val items = tracks.map(UnifiedTrack::toMediaItem)
        // With nothing queued there is no "next" to insert before, so this is just a play.
        if (ctrl.mediaItemCount == 0) {
            ctrl.setMediaItems(items)
            ctrl.prepare()
            ctrl.play()
        } else {
            ctrl.addMediaItems((ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount), items)
        }
    }

    fun removeFromQueue(index: Int) {
        _controller.value?.removeMediaItem(index)
    }

    fun clearQueue() {
        _controller.value?.clearMediaItems()
        lastQueue = emptyList()
    }

    /**
     * Enough of the player's state to put it back exactly as it was.
     *
     * A jam borrows this device's queue rather than adding to it, so what was playing before has to
     * be kept somewhere to give back. Nothing here is persisted: a jam lasts as long as the app is
     * in it, and restoring yesterday's queue would be worse than restoring nothing.
     */
    data class QueueSnapshot(
        val tracks: List<UnifiedTrack>,
        val index: Int,
        val positionMs: Long
    )

    /** What is loaded right now, or null when there is nothing worth putting back. */
    fun snapshotQueue(): QueueSnapshot? {
        val ctrl = _controller.value ?: return null
        val tracks = lastQueue.ifEmpty { return null }
        return QueueSnapshot(
            tracks = tracks,
            index = ctrl.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = ctrl.currentPosition.coerceAtLeast(0L)
        )
    }

    /**
     * Puts a snapshot back, at the track and position it was taken from.
     *
     * Goes through the private path rather than [play], which in a jam would propose the whole
     * queue to the room instead of playing it.
     */
    fun restoreQueue(snapshot: QueueSnapshot) {
        val ctrl = _controller.value ?: return
        if (snapshot.tracks.isEmpty()) return
        snapshot.tracks.forEach { trackCache[it.id] = it }
        ctrl.setMediaItems(
            snapshot.tracks.map(UnifiedTrack::toMediaItem),
            snapshot.index.coerceIn(0, snapshot.tracks.lastIndex),
            snapshot.positionMs
        )
        ctrl.prepare()
        ctrl.play()
    }

    /**
     * While following a friend, the transport belongs to them.
     *
     * Set by `ListenAlongController` for as long as a session lasts. The player stays fully
     * visible and expandable — you should be able to see what you are hearing — but pause, skip
     * and seek are inert, because acting on them would fight the host's next frame and leave the
     * two devices quietly out of step with no indication why.
     *
     * Enforced here rather than by disabling controls in each composable: there are several, in
     * the mini strip and the full screen, and one that forgot would silently break the session.
     * Starting *different* music is not blocked — see [play] — because that is an unambiguous
     * decision to stop following.
     */
    var isFollowing: Boolean = false
        private set

    /**
     * Called by the listen-along session as it starts and ends.
     *
     * [onLeave] is invoked when the user starts different music, so the session ends itself rather
     * than lingering as a banner over playback it is no longer driving.
     */
    fun setFollowing(following: Boolean, onLeave: (() -> Unit)? = null) {
        isFollowing = following
        onLeaveFollowing = if (following) onLeave else null
    }

    private var onLeaveFollowing: (() -> Unit)? = null

    /**
     * Set while in a jam: what to do when the user picks something to play.
     *
     * Null when not in one, so ordinary playback is untouched.
     */
    private var onPlayInJam: ((List<UnifiedTrack>, Int) -> Unit)? = null

    /**
     * [propose] is given the list and the index of the track the user actually chose. The index is
     * part of the contract rather than left to the caller to infer: without it this proposed the
     * first track of whatever row was tapped.
     */
    fun setJamProposal(propose: ((List<UnifiedTrack>, Int) -> Unit)?) {
        onPlayInJam = propose
    }

    /**
     * The jam's own playback, which must not be re-proposed back into the jam it came from.
     *
     * [startPositionMs] is the room's position, not zero: joining a jam halfway through a song
     * should drop you in where everyone else is.
     */
    internal fun playForJam(tracks: List<UnifiedTrack>, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        val ctrl = _controller.value ?: return
        tracks.forEach { trackCache[it.id] = it }
        ctrl.setMediaItems(tracks.map(UnifiedTrack::toMediaItem), 0, startPositionMs)
        ctrl.prepare()
        ctrl.play()
    }

    /**
     * Where the player actually is, for code that has to compare it against somebody else's clock.
     *
     * Read straight off the controller rather than from [state], which carries no position — the
     * position flow is sampled for the UI and is deliberately coarse. Null when nothing is bound.
     */
    internal fun currentPositionMs(): Long? = _controller.value?.currentPosition

    /** Whether audio is actually coming out right now. */
    internal fun isPlayingNow(): Boolean = _controller.value?.isPlaying == true

    /** What the follower's own session is allowed to do, bypassing [isFollowing]. */
    internal fun followerSeek(positionMs: Long) {
        _controller.value?.seekTo(positionMs)
    }

    internal fun followerSetPlaying(shouldPlay: Boolean) {
        val ctrl = _controller.value ?: return
        if (ctrl.isPlaying != shouldPlay) {
            // Rejoins the edge on resume for the same reason [togglePlayPause] does, and it
            // matters more here: the room carried on broadcasting while this device was paused,
            // so resuming where it stopped is both a dead position and one nobody else is at.
            if (shouldPlay) ctrl.resumeAtLiveEdge() else ctrl.pause()
        }
    }

    fun togglePlayPause() {
        if (isFollowing) return
        val ctrl = _controller.value ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.resumeAtLiveEdge()
    }

    /**
     * Resumes, rejoining the live edge first when what is playing is a broadcast.
     *
     * A paused livestream does not wait. The player holds the position it was paused at, and the
     * broadcaster keeps only a window — pause for longer than that window and resuming asks for
     * segments that no longer exist, which surfaces as a source error a few seconds in. There is
     * no "where I left off" for a broadcast anyway: leaving and coming back means hearing what is
     * on air now, exactly as it does on a radio.
     */
    private fun MediaController.resumeAtLiveEdge() {
        if (state.value.currentTrack?.isLive == true) seekToDefaultPosition()
        play()
    }

    fun seekTo(positionMs: Long) {
        if (isFollowing) return
        _controller.value?.seekTo(positionMs)
    }

    fun seekToIndex(index: Int) {
        if (isFollowing) return
        _controller.value?.seekToDefaultPosition(index)
    }

    fun next() {
        if (isFollowing) return
        _controller.value?.seekToNextMediaItem()
    }

    /**
     * Whether [previous] would restart the current track rather than step back to another one.
     *
     * Exposed because the swipe gesture has to show where it is about to land *before* the player
     * has moved, and "previous" means two different things depending on the position. Read at the
     * moment a gesture starts, never in composition — it changes with playback position.
     */
    val restartsOnPrevious: Boolean
        get() = (_controller.value?.currentPosition ?: 0L) > RESTART_THRESHOLD_MS

    /** Restarts the track when we are past the intro, otherwise steps back — the usual convention. */
    fun previous() {
        if (isFollowing) return
        val ctrl = _controller.value ?: return
        if (restartsOnPrevious) ctrl.seekTo(0L) else ctrl.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val ctrl = _controller.value ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val ctrl = _controller.value ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setRadioMode(enabled: Boolean) {
        secureStorage.setRadioMode(enabled)
    }

    /**
     * Said by the instant-radio button when there is nothing to build a station out of.
     *
     * Lives here rather than in the calling ViewModel because [notices] is the shell's one
     * subscription for this kind of message, and a second channel would mean a second collector
     * for the same snackbar.
     */
    fun notifyNoStation() {
        _notices.tryEmit("Not enough listening yet — play a few tracks and try again")
    }

    fun toggleRadio() {
        val enabled = !secureStorage.isRadioMode.value
        secureStorage.setRadioMode(enabled)
        // The toggle lives on a long press and changes nothing you can see until the queue runs
        // out, so without this it was impossible to tell whether the press had registered at all.
        _notices.tryEmit(
            if (enabled) "Radio mode on — the queue keeps going"
            else "Radio mode off"
        )
    }

    /**
     * Playback rate and pitch, as one pair.
     *
     * Media3 carries both in a single `PlaybackParameters`, so setting one has to restate the
     * other or it snaps back to 1.0. Exposed as a StateFlow because the controls that set it are
     * a transient popup — it has to survive being dismissed and reopened, and it is not part of
     * the per-track snapshot in [PlaybackState].
     *
     * Offload is switched off for anything but 1.0×: the DSP plays the stream untouched, so a
     * rate change silently does nothing while it is on.
     */
    private val _speedAndPitch = MutableStateFlow(SpeedAndPitch())
    val speedAndPitch: StateFlow<SpeedAndPitch> = _speedAndPitch.asStateFlow()

    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        val ctrl = _controller.value ?: return
        val clamped = SpeedAndPitch(
            speed = speed.coerceIn(MIN_RATE, MAX_RATE),
            pitch = pitch.coerceIn(MIN_RATE, MAX_RATE)
        )
        setOffloadEnabled(clamped.isDefault)
        ctrl.playbackParameters = PlaybackParameters(clamped.speed, clamped.pitch)
        _speedAndPitch.value = clamped
    }

    /**
     * Offload saves power but starves the visualizer, so the two are mutually exclusive.
     *
     * A livestream vetoes it outright, whoever asked: offload expects a track that ends, and an
     * HLS live window is not one — with it on the player reaches the end of the first window and
     * declares the item finished. `PlaybackCoordinator` is where the decision normally lives, but
     * `setSpeedAndPitch` also turns offload back on when the rate returns to 1.0x, and that must
     * not quietly re-break a live stream.
     */
    fun setOffloadEnabled(enabled: Boolean) {
        val ctrl = _controller.value ?: return
        val live = state.value.currentTrack?.isLive == true
        val allowed = enabled && !live
        // Video is suppressed here rather than anywhere else because this is the single writer of
        // `trackSelectionParameters` — two writers would each clobber the other's decision. See
        // [PlayerFactory.withVideoSuppressed] for why a livestream needs it at all.
        ctrl.trackSelectionParameters = PlayerFactory.withVideoSuppressed(
            PlayerFactory.withOffload(ctrl.trackSelectionParameters, allowed),
            live
        )
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L

        /**
         * Both ways a container can fail to parse.
         *
         * A manifest handed to the progressive extractors reports "unsupported" on most builds and
         * "malformed" on some, depending on how far the sniff gets before it gives up. Matching one
         * of the two left the retry not firing at all on devices that reported the other.
         */
        val CONTAINER_PARSE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        )

        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
    }
}

/**
 * The most specific message available.
 *
 * Media3 wraps the real cause in a generic "Source error", so the useful text — the reason a
 * source refused to resolve a stream — is several levels down. Deliberately no URLs or headers:
 * those carry credentials.
 */
private fun PlaybackException.userMessage(): String {
    var cause: Throwable? = this
    var best: String? = null
    while (cause != null) {
        cause.message?.takeIf { it.isNotBlank() }?.let { best = it }
        cause = cause.cause
    }
    val raw = best ?: return "Playback failed ($errorCodeName)."
    return raw.asActionableMessage()
}

/**
 * Turns the deepest cause into something the user can act on.
 *
 * The raw text is an ExoPlayer or OkHttp string written for a log, so surfacing it verbatim told
 * the user nothing — a signed-out YouTube Music session read as an unexplained HTTP number.
 * Anything unrecognised is still passed through rather than replaced by a vague catch-all.
 */
private fun String.asActionableMessage(): String = when {
    contains("YouTube Music refused", ignoreCase = true) &&
        (contains("401") || contains("403")) ->
        "Sign in to YouTube Music again — the session expired."

    contains("no playable audio", ignoreCase = true) ->
        "This track isn't playable from YouTube Music."

    // A signature or throttling-nonce transform failing is transient — YouTube rotated its player
    // JS — and retrying picks up the new one, which is very different advice from "unplayable".
    contains("unscramble", ignoreCase = true) ||
        contains("throttling parameter", ignoreCase = true) ->
        "Couldn't prepare the YouTube stream. Try again in a moment."

    contains("will not play this track", ignoreCase = true) ->
        "YouTube Music won't play this track here."

    contains("Response code: 403") || contains("Response code: 410") ->
        "Stream expired. Play it again to refresh it."

    contains("Unable to connect", ignoreCase = true) ||
        contains("UnknownHost", ignoreCase = true) ->
        "Can't reach the source. Check your connection."

    else -> this
}

private fun Player.buildSnapshot(
    radio: Boolean,
    cachedQueue: List<UnifiedTrack>,
    cache: java.util.concurrent.ConcurrentHashMap<String, UnifiedTrack>
): PlaybackState {
    val activeItem = runCatching { currentMediaItem }.getOrNull()
    val playing = runCatching { isPlaying }.getOrDefault(false)
    val buffering = runCatching { playbackState == Player.STATE_BUFFERING }.getOrDefault(false)
    val dur = runCatching { duration }.getOrDefault(0L).coerceAtLeast(0L)
    val curIndex = runCatching { currentMediaItemIndex }.getOrDefault(0)
    val shuffle = runCatching { shuffleModeEnabled }.getOrDefault(false)
    val repMode = runCatching { repeatMode }.getOrDefault(Player.REPEAT_MODE_OFF)

    return PlaybackState(
        currentTrack = activeItem?.resolveTrack(cache),
        queue = cachedQueue,
        currentIndex = curIndex,
        isPlaying = playing,
        isBuffering = buffering,
        durationMs = dur,
        isShuffle = shuffle,
        repeatMode = when (repMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        },
        isRadioMode = radio
    )
}

private fun Player.queueTracks(cache: java.util.concurrent.ConcurrentHashMap<String, UnifiedTrack>): List<UnifiedTrack> =
    (0 until mediaItemCount).mapNotNull { getMediaItemAt(it).resolveTrack(cache) }

private fun androidx.media3.common.MediaItem.resolveTrack(
    cache: java.util.concurrent.ConcurrentHashMap<String, UnifiedTrack>
): UnifiedTrack? {
    cache[mediaId]?.let { return it }
    val track = toUnifiedTrack() ?: return null
    cache[mediaId] = track
    return track
}
