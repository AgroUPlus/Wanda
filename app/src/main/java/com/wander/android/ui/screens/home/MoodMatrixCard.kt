package com.wander.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.MoodPreset
import com.wander.android.data.repository.MoodPresets
import com.wander.android.ui.components.Artwork
import kotlin.math.roundToInt

/**
 * Home's tactile Tempo × Energy pad. Dragging the puck previews a live "Mood Radio"; the four
 * pills jump straight to a named point on the same axes rather than being a separate feature.
 */
@Composable
fun MoodMatrixCard(
    modifier: Modifier = Modifier,
    viewModel: MoodMatrixViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.mood_matrix_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.mood_matrix_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            MoodPad(
                tempo = state.tempo,
                energy = state.energy,
                onMove = viewModel::setPosition
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MoodPresets, key = { it.key }) { preset ->
                    SuggestionChip(
                        onClick = { viewModel.setPosition(preset.tempo, preset.energy) },
                        label = { Text(preset.label()) },
                        colors = SuggestionChipDefaults.suggestionChipColors()
                    )
                }
            }

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
private fun MoodPad(
    tempo: Float,
    energy: Float,
    onMove: (tempo: Float, energy: Float) -> Unit
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                )
            )
            .onSizeChanged { sizePx = it }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (sizePx.width == 0 || sizePx.height == 0) return@detectDragGestures
                    val t = (change.position.x / sizePx.width).coerceIn(0f, 1f)
                    // Screen Y grows downward; energy grows upward, so this is inverted.
                    val e = (1f - change.position.y / sizePx.height).coerceIn(0f, 1f)
                    onMove(t, e)
                }
            }
    ) {
        val puckRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
        val puckOffset = Offset(
            x = tempo * sizePx.width - puckRadiusPx,
            y = (1f - energy) * sizePx.height - puckRadiusPx
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .offset { IntOffset(puckOffset.x.roundToInt(), puckOffset.y.roundToInt()) }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

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
