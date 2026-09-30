package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The playback helpers a [PlaybackEventListener] reads and writes while turning player events into state. */
internal class PlaybackCollaborators(
    val queueManager: PlayerQueueManager,
    val retryHandler: LivePlaybackRetryHandler,
    val checkpointTracker: EpisodeCheckpointTracker,
    val actualAudioFormat: MutableStateFlow<ActualAudioFormat?>
)

/**
 * Translates Media3 [Player.Listener] events of one [MediaController] into [PlaybackState] snapshots,
 * keeping the queue mirror, audio-format reset and episode checkpoints in step with the player.
 */
internal class PlaybackEventListener(
    private val controller: MediaController,
    private val collaborators: PlaybackCollaborators,
    private val isRadioMode: StateFlow<Boolean>,
    private val isLiveNow: () -> Boolean,
    private val onError: (String) -> Unit,
    private val emit: (PlaybackState) -> Unit
) : Player.Listener {

    private val queueManager get() = collaborators.queueManager
    private val checkpointTracker get() = collaborators.checkpointTracker
    private var seekEpoch = 0L

    /** Seeds the queue mirror and emits the first snapshot; call right after registering the listener. */
    fun start() {
        queueManager.lastQueue = PlaybackSnapshotBuilder.queueTracks(controller, queueManager.trackCache)
        emitSnapshot(controller)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (timelineChanged(player, events)) {
            queueManager.lastQueue = PlaybackSnapshotBuilder.queueTracks(player, queueManager.trackCache)
        }
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) collaborators.actualAudioFormat.value = null
        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) seekEpoch++
        checkpointTracker.rememberDuration(currentTrack(player), player.duration)
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
            checkpointTracker.checkpoint(currentTrack(player), player.currentPosition, player.duration)
        }
        emitSnapshot(player)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        val sameItemSeek = reason == Player.DISCONTINUITY_REASON_SEEK &&
            oldPosition.mediaItemIndex == newPosition.mediaItemIndex
        if (sameItemSeek) return
        checkpointTracker.checkpoint(
            queueManager.lastQueue.getOrNull(oldPosition.mediaItemIndex),
            oldPosition.positionMs,
            C.TIME_UNSET
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        val retry = collaborators.retryHandler
        if (retry.retryContainerMismatch(controller, error)) return
        if (retry.rejoinLiveEdge(controller, isLiveNow())) return
        onError(PlaybackErrorTranslator.userMessage(error))
    }

    private fun timelineChanged(player: Player, events: Player.Events): Boolean =
        events.contains(Player.EVENT_TIMELINE_CHANGED) ||
            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
            queueManager.lastQueue.size != player.mediaItemCount

    private fun currentTrack(player: Player) = queueManager.lastQueue.getOrNull(player.currentMediaItemIndex)

    private fun emitSnapshot(player: Player) {
        emit(
            PlaybackSnapshotBuilder.buildSnapshot(
                player,
                isRadioMode.value,
                queueManager.lastQueue,
                queueManager.trackCache,
                seekEpoch
            )
        )
    }
}
