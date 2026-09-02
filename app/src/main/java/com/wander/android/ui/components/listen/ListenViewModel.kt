package com.wander.android.ui.components.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.Recognition
import com.wander.android.data.repository.RecognitionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data object Listening : ListenState

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
    val state: StateFlow<ListenState> = _state.asStateFlow()

    /**
     * How much of the library recognition can actually see.
     *
     * Shown on the sheet rather than buried in Settings, because it is the single fact that
     * explains a failed match: an index covering nine tracks cannot identify the tenth, and
     * without it the feature would simply look broken.
     */
    val indexedTracks: StateFlow<Int> = recognitionRepository.indexedTrackCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
