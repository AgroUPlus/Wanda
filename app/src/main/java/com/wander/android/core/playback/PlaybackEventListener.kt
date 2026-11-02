package com.wander.android.core.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The playback helpers a [PlaybackEventListener] reads and writes while turning player events into state. */
internal class PlaybackCollaborators(
    val queueManager: PlayerQueueManager,
    val retryHandler: LivePlaybackRetryHandler,
    val actualAudioFormat: MutableStateFlow<ActualAudioFormat?>,
    val errorRecovery: TrackErrorRecovery
)

/**
 * Translates Media3 [Player.Listener] events of one [MediaController] into [PlaybackState] snapshots,
 * keeping the queue mirror and audio-format reset in step with the player. Episode progress is
 * saved by the service's own player instead; see [EpisodeProgressRecorder].
 */
internal class PlaybackEventListener(
    private val controller: MediaController,
    private val collaborators: PlaybackCollaborators,
    private val isRadioMode: StateFlow<Boolean>,
    private val isLiveNow: () -> Boolean,
    private val isOrderLocked: () -> Boolean,
    private val onError: (PlaybackMessage) -> Unit,
    private val emit: (PlaybackState) -> Unit
) : Player.Listener {

    private val queueManager get() = collaborators.queueManager
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
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            collaborators.actualAudioFormat.value = null
            collaborators.errorRecovery.onItemChanged()
        }
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_READY) {
            collaborators.errorRecovery.onItemReady()
        }
        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) seekEpoch++
        emitSnapshot(player)
    }

    override fun onPlayerError(error: PlaybackException) {
        val retry = collaborators.retryHandler
        if (retry.retryContainerMismatch(controller, error)) return
        if (retry.rejoinLiveEdge(controller, isLiveNow())) return
        val recovery = collaborators.errorRecovery
        if (recovery.refreshExpiredStream(controller, error)) return
        onError(PlaybackErrorTranslator.userMessage(error))
        recovery.skipPastFailedItem(controller, error, isLive = isLiveNow(), orderLocked = isOrderLocked())
    }

    private fun timelineChanged(player: Player, events: Player.Events): Boolean =
        events.contains(Player.EVENT_TIMELINE_CHANGED) ||
            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
            queueManager.lastQueue.size != player.mediaItemCount

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
