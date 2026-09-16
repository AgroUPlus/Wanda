package com.wander.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MoodPreset
import com.wander.android.data.repository.MoodPresets
import com.wander.android.data.repository.MoodRadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoodMatrixUiState(
    /** Null until a mood is picked — no chip is shown selected and no preview loads. */
    val selectedKey: String? = null,
    val preview: List<UnifiedTrack> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * Backs [MoodMatrixCard]. Picking a mood is a single discrete tap on an enum-style chip — not a
 * finger dragged across a 2D pad, which nobody actually used — but the query underneath is still
 * a point on the tempo/energy plane; [MoodPreset] is just the named point that tap selects, and
 * [MoodRadioRepository] answers the same way across every connected source, never just one.
 */
@HiltViewModel
class MoodMatrixViewModel @Inject constructor(
    private val moodRadioRepository: MoodRadioRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _state = MutableStateFlow(MoodMatrixUiState())
    val state: StateFlow<MoodMatrixUiState> = _state.asStateFlow()

    private var queryJob: Job? = null

    fun selectMood(preset: MoodPreset) {
        _state.value = _state.value.copy(selectedKey = preset.key, isLoading = true)
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val preview = moodRadioRepository.radioFor(preset.tempo, preset.energy, limit = PREVIEW_LIMIT)
            _state.value = _state.value.copy(preview = preview, isLoading = false)
        }
    }

    /** Plays the full radio, not just the [PREVIEW_LIMIT] shown as avatars. */
    fun playMoodRadio() {
        val preset = MoodPresets.firstOrNull { it.key == _state.value.selectedKey } ?: return
        viewModelScope.launch {
            val tracks = moodRadioRepository.radioFor(preset.tempo, preset.energy, limit = QUEUE_LIMIT)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    private companion object {
        const val PREVIEW_LIMIT = 3
        const val QUEUE_LIMIT = 30
    }
}
