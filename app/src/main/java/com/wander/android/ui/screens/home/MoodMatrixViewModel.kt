package com.wander.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MoodRadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoodMatrixUiState(
    val tempo: Float = 0.5f,
    val energy: Float = 0.5f,
    val preview: List<UnifiedTrack> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * Backs [MoodMatrixCard]. Debounces the drag itself, in the ViewModel rather than the composable,
 * so the same debounce covers both a finger drag and a preset tap without the card having to know
 * which one it was.
 */
@HiltViewModel
class MoodMatrixViewModel @Inject constructor(
    private val moodRadioRepository: MoodRadioRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _state = MutableStateFlow(MoodMatrixUiState())
    val state: StateFlow<MoodMatrixUiState> = _state.asStateFlow()

    private var queryJob: Job? = null

    /** [tempo] and [energy] are normalised `0..1`, matching the matrix's own axes. */
    fun setPosition(tempo: Float, energy: Float) {
        _state.value = _state.value.copy(tempo = tempo, energy = energy, isLoading = true)
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val preview = moodRadioRepository.radioFor(tempo, energy, limit = PREVIEW_LIMIT)
            _state.value = _state.value.copy(preview = preview, isLoading = false)
        }
    }

    /** Plays the full radio, not just the [PREVIEW_LIMIT] shown as avatars. */
    fun playMoodRadio() {
        val current = _state.value
        viewModelScope.launch {
            val tracks = moodRadioRepository.radioFor(current.tempo, current.energy, limit = QUEUE_LIMIT)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
        const val PREVIEW_LIMIT = 3
        const val QUEUE_LIMIT = 30
    }
}
