package com.wander.android.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.model.UnifiedTrack
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject
import javax.inject.Singleton

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

    fun togglePlayPause() {
        val ctrl = _controller.value ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(positionMs: Long) {
        _controller.value?.seekTo(positionMs)
    }

    fun seekToIndex(index: Int) {
        _controller.value?.seekToDefaultPosition(index)
    }

    fun next() {
        _controller.value?.seekToNextMediaItem()
    }

    /** Restarts the track when we are past the intro, otherwise steps back — the usual convention. */
    fun previous() {
        val ctrl = _controller.value ?: return
        if (ctrl.currentPosition > RESTART_THRESHOLD_MS) ctrl.seekTo(0L) else ctrl.seekToPreviousMediaItem()
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

    fun toggleRadio() {
        secureStorage.setRadioMode(!secureStorage.isRadioMode.value)
    }

    /** Offload saves power but starves the visualizer, so the two are mutually exclusive. */
    fun setOffloadEnabled(enabled: Boolean) {
        val ctrl = _controller.value ?: return
        ctrl.trackSelectionParameters =
            PlayerFactory.withOffload(ctrl.trackSelectionParameters, enabled)
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
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
