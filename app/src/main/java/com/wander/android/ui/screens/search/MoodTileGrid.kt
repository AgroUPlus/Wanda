package com.wander.android.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.MoodPreset
import com.wander.android.ui.components.icon
import com.wander.android.ui.components.label

/** The moods two to a row, each a tile that starts its radio. */
@Composable
internal fun MoodTileGrid(
    moods: List<MoodPreset>,
    playingMoodKey: String?,
    onPlayMood: (MoodPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        moods.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { column, mood ->
                    MoodTile(
                        mood = mood,
                        index = row * 2 + column,
                        playing = mood.key == playingMoodKey,
                        onClick = { onPlayMood(mood) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // An odd last mood keeps its half width rather than stretching across the row.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One mood. The big shape bleeding off its top corner is decoration only — each tile gets a
 * different one so the four read apart at a glance, before their labels are read.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoodTile(
    mood: MoodPreset,
    index: Int,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        playing -> colors.primary to colors.onPrimary
        index % 3 == 0 -> colors.primaryContainer to colors.onPrimaryContainer
        index % 3 == 1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val decoration = when (index % 4) {
        0 -> MaterialShapes.Cookie9Sided
        1 -> MaterialShapes.Clover4Leaf
        2 -> MaterialShapes.Sunny
        else -> MaterialShapes.SoftBurst
    }.toShape()
    val name = mood.label()
    val description = stringResource(R.string.search_mood_radio_start, name)

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        modifier = modifier
            .height(112.dp)
            .semantics {
                contentDescription = description
                selected = playing
            }
    ) {
        Box {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 28.dp, y = (-28).dp)
                    .size(104.dp)
                    .clip(decoration)
                    .background(content.copy(alpha = DECORATION_ALPHA))
            )
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(mood.icon(), contentDescription = null)
                Spacer(Modifier.height(28.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val DECORATION_ALPHA = 0.12f
