package com.wander.android.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.repository.MoodPreset
import com.wander.android.data.repository.MoodPresets
import com.wander.android.ui.components.SelectableChip
import com.wander.android.ui.components.TravelingHighlight
import com.wander.android.ui.components.rememberTravelingHighlightState

/**
 * Home's mood picker: a compact row of named presets — tapping one starts its radio immediately,
 * the same "pick a mood, it plays" pattern YouTube Music uses. No card, no drag surface, no
 * separate confirm step — this used to be a 2D pad nobody dragged a finger across.
 *
 * Uses [Row] plus [Modifier.horizontalScroll] rather than a `LazyRow`, matching
 * [com.wander.android.ui.components.SourceFilterChips] exactly: [TravelingHighlight] measures each
 * chip's position with `onGloballyPositioned`, relative to the shared scrolling container. A
 * `LazyRow` scrolls its own content in a coordinate space one level further in, so the same
 * highlight positioned against it would drift out of alignment with its chip as the row scrolled —
 * which is what made the pill visibly "move" independently on a drag.
 */
@Composable
fun MoodMatrixCard(modifier: Modifier = Modifier) {
    val viewModel: MoodMatrixViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val highlightState = rememberTravelingHighlightState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        TravelingHighlight(state = highlightState, selectedKey = state.playingKey)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoodPresets.forEach { preset ->
                SelectableChip(
                    label = preset.label(),
                    selected = state.playingKey == preset.key,
                    onClick = { viewModel.playMood(preset) },
                    highlightState = highlightState,
                    key = preset.key
                )
            }
        }
    }
}

/** The localized label for a [MoodPreset] — the one place its [MoodPreset.key] becomes text. */
@Composable
private fun MoodPreset.label(): String = stringResource(
    when (key) {
        "late_night_chill" -> R.string.mood_preset_late_night_chill
        "focus_flow" -> R.string.mood_preset_focus_flow
        "morning_energizer" -> R.string.mood_preset_morning_energizer
        "high_voltage" -> R.string.mood_preset_high_voltage
        else -> R.string.mood_preset_late_night_chill
    }
)
