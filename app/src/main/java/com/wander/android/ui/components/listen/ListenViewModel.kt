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
import com.wander.android.data.repository.RecognitionProgress
import kotlinx.coroutines.CancellationException
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

    /**
     * The microphone is still open and the matcher has an opinion.
     *
     * Its own state rather than a field on [Listening], because the screen it draws is genuinely
     * different: a shortlist that reorders as the audio arrives, not a spinner.
     */
    data class Narrowing(val progress: RecognitionProgress) : ListenState
    data class Matched(val recognition: Recognition) : ListenState
    data object NoMatch : ListenState
    data object Failed : ListenState

    /**
     * Which panel this state draws, ignoring what it carries.
     *
     * `Narrowing` changes value every second while showing the same panel, so the screen's
     * cross-fade has to be told those are the same thing or it restarts the transition on each
     * new ranking.
     */
    val kind: String
        get() = when (this) {
            Idle, Listening -> "listening"
            is Narrowing -> "listening"
            is Matched -> "matched"
            NoMatch -> "nomatch"
            Failed -> "failed"
        }
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
            // Collected rather than awaited: every emission is the matcher's real state at that
            // moment, and showing it is the whole difference between a shortlist narrowing and a
            // spinner that produces an answer from nowhere.
            val outcome = runCatching {
                recognitionRepository.listenProgressively().collect { progress ->
                    _state.value = if (progress.settled) {
                        when (val recognition = progress.recognition) {
                            null -> ListenState.NoMatch
                            else -> ListenState.Matched(recognition)
                        }
                    } else {
                        // Nothing to show yet keeps the plain listening screen: an empty shortlist
                        // reads as "found nothing" rather than "not yet".
                        if (progress.candidates.isEmpty()) ListenState.Listening
                        else ListenState.Narrowing(progress)
                    }
                }
            }
            // A cancelled collection is the user dismissing the sheet, which `stop` has already
            // put back to idle — it must not be reported as a microphone failure.
            if (outcome.isFailure && outcome.exceptionOrNull() !is CancellationException) {
                _state.value = ListenState.Failed
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
