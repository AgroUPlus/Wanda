package com.wander.android.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.StatEntry
import com.wander.android.ui.components.scrollingTitle

/**
 * A row of bars scaled to the largest value in the set.
 *
 * Scaled to the set rather than to a fixed ceiling: a quiet week and a heavy one are both worth
 * reading the shape of, and a shared axis flattens the quiet one into a flat line.
 *
 * Drawn on a `Canvas` rather than as a `Row` of boxes — fourteen to twenty-four of those is a
 * measurable amount of layout for something that is one drawing operation.
 */
@Composable
internal fun BarChart(
    values: List<Long>,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val peak = values.max().coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(modifier = modifier.fillMaxWidth().height(88.dp)) {
        val gap = 3.dp.toPx()
        val slot = (size.width - gap * (values.size - 1)) / values.size
        val radius = CornerRadius(3.dp.toPx())

        values.forEachIndexed { index, value ->
            val left = index * (slot + gap)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(left, 0f),
                size = Size(slot, size.height),
                cornerRadius = radius
            )
            // A nonzero value would otherwise round away to an invisible sliver.
            val height = if (value == 0L) 0f else {
                (value.toFloat() / peak * size.height).coerceAtLeast(2.dp.toPx())
            }
            if (height > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - height),
                    size = Size(slot, height),
                    cornerRadius = radius
                )
            }
        }
    }
}

/** A ranked list: position, name, and what it is ranked by. */
@Composable
internal fun TopList(
    entries: List<StatEntry>,
    valueLabel: (Long) -> String,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        Text(
            text = "Nothing yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEachIndexed { index, entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f).scrollingTitle()
                )
                Text(
                    text = valueLabel(entry.value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Seconds as something a person reads: `3h 12m`, or `12m` when there are no hours. */
internal fun formatListeningTime(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
