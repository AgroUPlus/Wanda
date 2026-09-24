package com.wander.android.core.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Builds the reactive [StateFlow] of [PlaybackState] by listening to Media3 [Player.Listener] events
 * and combining radio mode, order locks, resolved livestreams, and audio formats.
 */
internal object PlaybackStateFlowBuilder {

    fun create(
        controller: StateFlow<MediaController?>,
        queueManager: PlayerQueueManager,
        retryHandler: LivePlaybackRetryHandler,
        checkpointTracker: EpisodeCheckpointTracker,
        actualAudioFormat: MutableStateFlow<ActualAudioFormat?>,
        orderLocked: StateFlow<Boolean>,
        isRadioMode: StateFlow<Boolean>,
        resolvedLive: StateFlow<Set<String>>,
        onError: (String) -> Unit,
        scope: CoroutineScope
    ): StateFlow<PlaybackState> {
        lateinit var stateRef: StateFlow<PlaybackState>

        val flow = controller
            .flatMapLatest { ctrl ->
                if (ctrl == null) flowOf(PlaybackState()) else callbackFlow {
                    var seekEpoch = 0L
                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            val timelineChanged = events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                                queueManager.lastQueue.size != player.mediaItemCount
                            if (timelineChanged) {
                                queueManager.lastQueue = PlaybackSnapshotBuilder.queueTracks(player, queueManager.trackCache)
                            }
                            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                                actualAudioFormat.value = null
                            }
                            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) seekEpoch++
                            checkpointTracker.rememberDuration(
                                queueManager.lastQueue.getOrNull(player.currentMediaItemIndex),
                                player.duration
                            )
                            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                                checkpointTracker.checkpoint(
                                    queueManager.lastQueue.getOrNull(player.currentMediaItemIndex),
                                    player.currentPosition,
                                    player.duration
                                )
                            }
                            trySend(
                                PlaybackSnapshotBuilder.buildSnapshot(
                                    player,
                                    isRadioMode.value,
                                    queueManager.lastQueue,
                                    queueManager.trackCache,
                                    seekEpoch
                                )
                            )
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                                oldPosition.mediaItemIndex == newPosition.mediaItemIndex
                            ) {
                                return
                            }
                            checkpointTracker.checkpoint(
                                queueManager.lastQueue.getOrNull(oldPosition.mediaItemIndex),
                                oldPosition.positionMs,
                                C.TIME_UNSET
                            )
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            if (retryHandler.retryContainerMismatch(ctrl, error)) return
                            val isLive = stateRef.value.currentTrack?.isLive == true
                            if (retryHandler.rejoinLiveEdge(ctrl, isLive)) return
                            onError(PlaybackErrorTranslator.userMessage(error))
                        }
                    }
                    ctrl.addListener(listener)
                    queueManager.lastQueue = PlaybackSnapshotBuilder.queueTracks(ctrl, queueManager.trackCache)
                    trySend(
                        PlaybackSnapshotBuilder.buildSnapshot(
                            ctrl,
                            isRadioMode.value,
                            queueManager.lastQueue,
                            queueManager.trackCache,
                            seekEpoch
                        )
                    )
                    awaitClose { ctrl.removeListener(listener) }
                }
            }
            .combine(isRadioMode) { state, radio -> state.copy(isRadioMode = radio) }
            .combine(orderLocked) { state, locked -> state.copy(orderLocked = locked) }
            .combine(resolvedLive) { state, live -> state.withLiveIds(live) }
            .combine(actualAudioFormat) { state, format -> state.copy(actualAudioFormat = format) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, PlaybackState())

        stateRef = flow
        return flow
    }
}
