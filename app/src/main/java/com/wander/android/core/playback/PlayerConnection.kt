package com.wander.android.core.playback

import android.content.ComponentName
import android.content.Context
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The UI's handle on playback: a [MediaController] bound to [PlaybackService], exposed as flows.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val streamResolver: StreamResolver
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val queueManager = PlayerQueueManager()
    private val retryHandler = LivePlaybackRetryHandler()
    private val checkpointTracker = EpisodeCheckpointTracker()
    private val skipManager = SkipGraceManager()
    private val speedController = PlayerSpeedAndOffloadController()

    private val _orderLocked = MutableStateFlow(false)
    private val jamCoordinator = PlayerJamCoordinator(scope, _orderLocked, queueManager)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _actualAudioFormat = MutableStateFlow<ActualAudioFormat?>(null)
    val isRadioMode: StateFlow<Boolean> get() = secureStorage.isRadioMode

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    val episodeCheckpoints: SharedFlow<EpisodeCheckpoint> = checkpointTracker.episodeCheckpoints
    val speedAndPitch: StateFlow<SpeedAndPitch> = speedController.speedAndPitch

    data class QueueSnapshot(val tracks: List<UnifiedTrack>, val index: Int, val positionMs: Long)

    val state: StateFlow<PlaybackState> = PlaybackStateFlowBuilder.create(
        controller = _controller,
        queueManager = queueManager,
        retryHandler = retryHandler,
        checkpointTracker = checkpointTracker,
        actualAudioFormat = _actualAudioFormat,
        orderLocked = _orderLocked,
        isRadioMode = secureStorage.isRadioMode,
        resolvedLive = streamResolver.resolvedLive,
        onError = { _errors.tryEmit(it) },
        scope = scope
    )

    private data class PendingPlay(val tracks: List<UnifiedTrack>, val startIndex: Int, val startPositionMs: Long)
    private var pendingPlay: PendingPlay? = null

    fun connect() {
        if (_controller.value != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) {
                    _actualAudioFormat.value = ActualAudioFormat.fromBundle(extras)
                }
            })
            .buildAsync()
        future.addListener(
            {
                val ctrl = runCatching { future.get() }.getOrNull()
                ctrl?.let { c ->
                    val lang = secureStorage.preferredAudioLanguage
                    if (lang != null) {
                        c.trackSelectionParameters = c.trackSelectionParameters
                            .buildUpon()
                            .setPreferredAudioLanguage(lang)
                            .build()
                    }
                }
                _controller.value = ctrl
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
        queueManager.clear()
    }

    fun play(tracks: List<UnifiedTrack>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        jamCoordinator.onPlayInJam?.let { propose ->
            propose(tracks, startIndex.coerceIn(0, tracks.lastIndex))
            return
        }
        jamCoordinator.onUserInitiatedPlay()
        val ctrl = _controller.value ?: run {
            pendingPlay = PendingPlay(tracks, startIndex, startPositionMs)
            connect()
            return
        }
        queueManager.play(ctrl, tracks, startIndex, startPositionMs)
    }

    fun addToQueue(tracks: List<UnifiedTrack>) = _controller.value?.let { queueManager.addToQueue(it, tracks) }
    fun playNext(tracks: List<UnifiedTrack>) = _controller.value?.let { queueManager.playNext(it, tracks) }
    fun removeFromQueue(index: Int) = _controller.value?.let { queueManager.removeFromQueue(it, index) }
    fun insertInQueue(index: Int, track: UnifiedTrack) = _controller.value?.let { queueManager.insertInQueue(it, index, track) }
    fun moveInQueue(from: Int, to: Int) = _controller.value?.let { queueManager.moveInQueue(it, from, to, state.value.orderLocked) }
    fun clearQueue() = _controller.value?.let { queueManager.clearQueue(it) }

    fun snapshotQueue(): QueueSnapshot? {
        val s = queueManager.snapshotQueue(_controller.value) ?: return null
        return QueueSnapshot(s.tracks, s.index, s.positionMs)
    }

    fun restoreQueue(snapshot: QueueSnapshot) = _controller.value?.let { queueManager.restoreQueue(it, snapshot) }

    val isFollowing: Boolean get() = jamCoordinator.isFollowing
    fun setFollowing(following: Boolean, onLeave: (() -> Unit)? = null) = jamCoordinator.setFollowing(following, onLeave)

    val isInJam: Boolean get() = jamCoordinator.isInJam
    fun setJamProposal(propose: ((List<UnifiedTrack>, Int) -> Unit)?) = jamCoordinator.setJamProposal(_controller.value, propose)

    internal fun playForJam(tracks: List<UnifiedTrack>, startPositionMs: Long = 0L) =
        jamCoordinator.playForJam(_controller.value, tracks, startPositionMs)

    internal fun currentPositionMs(): Long? = _controller.value?.currentPosition
    internal fun isPlayingNow(): Boolean = _controller.value?.isPlaying == true
    internal fun followerSeek(positionMs: Long) = jamCoordinator.followerSeek(_controller.value, positionMs)

    internal fun followerSetPlaying(shouldPlay: Boolean) =
        jamCoordinator.followerSetPlaying(_controller.value, shouldPlay, state.value.currentTrack?.isLive == true)

    fun togglePlayPause() {
        if (isFollowing) return
        val ctrl = _controller.value ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.resumeAtLiveEdge()
    }

    private fun MediaController.resumeAtLiveEdge() {
        if (state.value.currentTrack?.isLive == true) seekToDefaultPosition()
        play()
    }

    fun seekTo(positionMs: Long) { if (!isFollowing) _controller.value?.seekTo(positionMs) }

    fun seekBy(deltaMs: Long) {
        if (isFollowing) return
        val ctrl = _controller.value ?: return
        ctrl.seekTo(EpisodeJumps.target(ctrl.currentPosition, deltaMs, ctrl.duration.coerceAtLeast(0L)))
    }

    fun seekToIndex(index: Int) { if (!isFollowing) _controller.value?.let { skipManager.seekToIndex(it, index) } }
    fun next() { if (!isFollowing) _controller.value?.let { skipManager.next(it) } }
    val restartsOnPrevious: Boolean get() = skipManager.restartsOnPrevious(_controller.value)
    fun previous() { if (!isFollowing) _controller.value?.let { skipManager.previous(it) } }
    fun previousTrack() { if (!isFollowing) _controller.value?.let { skipManager.previousTrack(it) } }

    fun toggleShuffle() {
        if (isFollowing || isInJam) return
        val ctrl = _controller.value ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun toggleRepeat() {
        if (isFollowing || isInJam) return
        val ctrl = _controller.value ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setRadioMode(enabled: Boolean) = secureStorage.setRadioMode(enabled)

    fun setPreferredAudioLanguage(language: String?) {
        secureStorage.preferredAudioLanguage = language
        val ctrl = _controller.value ?: return
        ctrl.trackSelectionParameters = ctrl.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(language)
            .build()
    }

    fun notifyNoStation() = _notices.tryEmit("Not enough listening yet — play a few tracks and try again")

    fun toggleRadio() {
        val enabled = !secureStorage.isRadioMode.value
        secureStorage.setRadioMode(enabled)
        _notices.tryEmit(if (enabled) "Radio mode on — the queue keeps going" else "Radio mode off")
    }

    fun setSpeedAndPitch(speed: Float, pitch: Float) =
        speedController.setSpeedAndPitch(_controller.value, speed, pitch, state.value.currentTrack?.isLive == true)

    fun setOffloadEnabled(enabled: Boolean) =
        speedController.setOffloadEnabled(_controller.value, enabled, state.value.currentTrack?.isLive == true)
}
