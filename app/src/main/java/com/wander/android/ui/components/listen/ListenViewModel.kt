package com.wander.android.ui.components.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.IndexReadiness
import com.wander.android.data.repository.Recognition
import com.wander.android.data.repository.RecognitionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the listening sheet is doing.
 *
 * [NoMatch] and [Failed] are kept apart on purpose. "I listened and this is not in your library"
 * and "I could not listen" are different facts about different problems, and collapsing them into
 * one message would tell a user with a muted microphone to go and buy the record.
 */
sealed interface ListenState {
    data object Idle : ListenState

    /** The microphone is open. */
    data object Listening : ListenState

    /**
     * The microphone has closed and the clip is being matched.
     *
     * Split out of [Listening] because they are different claims and only one of them was ever
     * true at a time. Recognition holds the microphone for six seconds and then searches, and the
     * sheet went on saying "Listening…" over a wave that had gone flat for the whole search —
     * so the one moment the user most needed to know something was still happening was the moment
     * the screen looked most like it had stopped.
     */
    data object Identifying : ListenState

    data class Matched(val recognition: Recognition) : ListenState
    data object NoMatch : ListenState
    data object Failed : ListenState
}

@HiltViewModel
class ListenViewModel @Inject constructor(
    private val recognitionRepository: RecognitionRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)

    /**
     * What the sheet shows.
     *
     * [ListenState.Listening] is resolved against the microphone rather than stored, because the
     * transition to [ListenState.Identifying] is not an event the recogniser reports — the capture
     * simply ends, possibly early, and the search carries on. Reading it from the recorder is what
     * keeps the label true at the moment it changes rather than one step behind.
     */
    val state: StateFlow<ListenState> =
        combine(_state, recognitionRepository.isRecording) { state, recording ->
            if (state == ListenState.Listening && !recording) ListenState.Identifying else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListenState.Idle)

    /**
     * How much of the library recognition can actually see, and why it might be nothing.
     *
     * Shown on the sheet rather than buried in Settings, because it is the single fact that
     * explains a failed match: an index covering nine tracks cannot identify the tenth, and
     * without it the feature would simply look broken.
     *
     * Starts at [IndexReadiness.Empty] rather than at a guess about the model: "the index is
     * filling" is the only one of the three that is harmless to show for the frame before the
     * real answer arrives.
     */
    val readiness: StateFlow<IndexReadiness> = recognitionRepository.indexReadiness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndexReadiness.Empty)

    /** Real-time microphone audio volume level `[0f, 1f]` during active capture. */
    val audioLevel: StateFlow<Float> = recognitionRepository.audioLevel

    private var listening: Job? = null


    fun start() {
        // One at a time: a second tap while the microphone is open would try to open it twice and
        // both recordings would be starved of input.
        if (listening?.isActive == true) return
        listening = viewModelScope.launch {
            _state.value = ListenState.Listening
            val result = runCatching { recognitionRepository.listen() }
            val recognition = result.getOrNull()
            _state.value = when {
                result.isFailure -> ListenState.Failed
                recognition == null -> ListenState.NoMatch
                else -> ListenState.Matched(recognition)
            }
        }
    }

    /** Cancels the recording. The recorder checks for this every chunk, so it stops promptly. */
    fun stop() {
        listening?.cancel()
        listening = null
        _state.value = ListenState.Idle
    }

    /**
     * Plays the identified track from where the room had got to, not from the top.
     *
     * The offset is the whole reason the matcher bothers to compute one — picking the song up
     * where it is playing is the difference between "you own this" and joining in.
     */
    fun playMatch() {
        val matched = _state.value as? ListenState.Matched ?: return
        playerConnection.play(
            tracks = listOf(matched.recognition.track),
            startPositionMs = matched.recognition.positionSeconds * 1000L
        )
    }
}
