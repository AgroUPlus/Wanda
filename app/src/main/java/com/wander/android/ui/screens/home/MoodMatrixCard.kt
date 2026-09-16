package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MoodPreset
import com.wander.android.data.repository.MoodPresets
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.SelectableChip
import com.wander.android.ui.components.TravelingHighlight
import com.wander.android.ui.components.rememberTravelingHighlightState

/**
 * Home's mood picker: a row of named presets, not a 2D pad nobody dragged a finger across.
 *
 * Each pill is a point on the tempo/energy plane [com.wander.android.data.repository.MoodRadioRepository]
 * already ranks against — picking one is exactly as expressive as the matrix was, just as a tap
 * instead of a gesture nobody discovered. Uses the same chip + gliding-highlight pattern as
 * [com.wander.android.ui.components.SourceFilterChips], so this reads as the same kind of control
 * as every other enum picker in the app.
 */
@Composable
fun MoodMatrixCard(
    modifier: Modifier = Modifier,
    viewModel: MoodMatrixViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val highlightState = rememberTravelingHighlightState()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.mood_matrix_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.mood_matrix_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                TravelingHighlight(state = highlightState, selectedKey = state.selectedKey)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MoodPresets, key = { it.key }) { preset ->
                        SelectableChip(
                            label = preset.label(),
                            selected = state.selectedKey == preset.key,
                            onClick = { viewModel.selectMood(preset) },
                            highlightState = highlightState,
                            key = preset.key
                        )
                    }
                }
            }

            if (state.selectedKey != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MoodPreviewAvatars(
                        tracks = state.preview,
                        modifier = Modifier.weight(1f)
                    )
                    ExtendedFloatingActionButton(
                        onClick = viewModel::playMoodRadio,
                        icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        text = { Text(stringResource(R.string.mood_matrix_play)) }
                    )
                }
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

@Composable
private fun MoodPreviewAvatars(tracks: List<UnifiedTrack>, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        if (tracks.isEmpty()) {
            Text(
                stringResource(R.string.mood_matrix_empty_preview),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            tracks.take(3).forEach { track ->
                Artwork(
                    url = track.artworkUrl,
                    contentDescription = track.title,
                    sizeDp = 40.dp,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 4.dp)
                )
            }
        }
    }
}
