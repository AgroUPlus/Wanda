package com.wander.android.core.playback

import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Builds the reactive [StateFlow] of [PlaybackState] by listening to Media3 player events through a
 * [PlaybackEventListener] and combining radio mode, order locks, resolved livestreams, and audio formats.
 */
internal object PlaybackStateFlowBuilder {

    fun create(
        controller: StateFlow<MediaController?>,
        collaborators: PlaybackCollaborators,
        orderLocked: StateFlow<Boolean>,
        isRadioMode: StateFlow<Boolean>,
        resolvedLive: StateFlow<Set<String>>,
        onError: (String) -> Unit,
        scope: CoroutineScope
    ): StateFlow<PlaybackState> {
        lateinit var stateRef: StateFlow<PlaybackState>

        val flow = controller
            .flatMapLatest { ctrl ->
                if (ctrl == null) {
                    flowOf(PlaybackState())
                } else {
                    snapshots(ctrl, collaborators, isRadioMode, { stateRef.value.currentTrack?.isLive == true }, onError)
                }
            }
            .combine(isRadioMode) { state, radio -> state.copy(isRadioMode = radio) }
            .combine(orderLocked) { state, locked -> state.copy(orderLocked = locked) }
            .combine(resolvedLive) { state, live -> state.withLiveIds(live) }
            .combine(collaborators.actualAudioFormat) { state, format -> state.copy(actualAudioFormat = format) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, PlaybackState())

        stateRef = flow
        return flow
    }

    private fun snapshots(
        ctrl: MediaController,
        collaborators: PlaybackCollaborators,
        isRadioMode: StateFlow<Boolean>,
        isLiveNow: () -> Boolean,
        onError: (String) -> Unit
    ): Flow<PlaybackState> = callbackFlow {
        val listener = PlaybackEventListener(ctrl, collaborators, isRadioMode, isLiveNow, onError) { trySend(it) }
        ctrl.addListener(listener)
        listener.start()
        awaitClose { ctrl.removeListener(listener) }
    }
}
