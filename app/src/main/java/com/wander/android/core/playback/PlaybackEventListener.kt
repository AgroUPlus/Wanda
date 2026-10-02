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
        // Not in a batch that also changed the playing item: by then `currentTrack` is already the
        // new item while `currentPosition` can still be the old one's, and that pairing would save
        // the previous episode's time as the new episode's progress. The old item was checkpointed
        // by `onPositionDiscontinuity`, so nothing is lost by skipping it here.
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying && !events.changesPlayingItem()) {
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

/** Whether this batch replaced, moved or re-timed the playing item rather than just changing its play state. */
internal fun Player.Events.changesPlayingItem(): Boolean = containsAny(*PLAYING_ITEM_EVENTS)

/**
 * The same rule over plain event ids. `Player.Events` is built on an Android collection that does
 * not exist in a JVM unit test, so the rule lives here where a test can reach it.
 */
internal fun changesPlayingItem(eventIds: Set<Int>): Boolean = PLAYING_ITEM_EVENTS.any { it in eventIds }

private val PLAYING_ITEM_EVENTS = intArrayOf(
    Player.EVENT_MEDIA_ITEM_TRANSITION,
    Player.EVENT_TIMELINE_CHANGED,
    Player.EVENT_POSITION_DISCONTINUITY
)
