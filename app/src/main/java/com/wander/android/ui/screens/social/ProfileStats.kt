package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.StatEntry

/**
 * The two numbers that summarise somebody's listening, as tiles rather than as a sentence.
 *
 * "1482 plays · 96 h" was one line of body text sitting under a heading, which is where a number
 * goes to be skipped. Two tiles give each figure its own weight and make the pair scannable at the
 * distance a profile is actually read from.
 */
@Composable
internal fun StatTiles(plays: Long, hours: Long) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        StatTile(value = plays.toString(), label = "plays", modifier = Modifier.weight(1f))
        StatTile(value = hours.toString(), label = "hours", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * A ranked list where the bar *is* the row.
 *
 * The old version was a name on the left and a count on the right, which is a table: to see that
 * the first artist is played three times as much as the fourth you had to read four numbers and do
 * the arithmetic. Drawing each row's share as the width of its own background says it without
 * being read, and the count stays on the right for anyone who wants the actual figure.
 *
 * Scaled against the top entry rather than the total, because the question a top-five answers is
 * "how do these compare to each other", not "what fraction of everything is this".
 */
@Composable
internal fun StatBars(title: String, entries: List<StatEntry>) {
    if (entries.isEmpty()) return
    val top = entries.maxOf { it.value }.coerceAtLeast(1L)

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        entries.forEachIndexed { index, entry ->
            StatBar(
                rank = index + 1,
                entry = entry,
                fraction = (entry.value.toFloat() / top).coerceIn(MinBar, 1f)
            )
        }
    }
}

@Composable
private fun StatBar(rank: Int, entry: StatEntry, fraction: Float) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // Laid out by hand rather than with `fillMaxWidth(fraction)` so the bar measures against
        // the row it sits in and not against whatever the parent happens to offer.
        Layout(
            content = {
                Box(
                    modifier = Modifier
                        .height(BarHeight)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(10.dp)
                        )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { measurables, constraints ->
            val width = (constraints.maxWidth * fraction).toInt().coerceAtLeast(1)
            val placeable = measurables.first().measure(
                constraints.copy(minWidth = width, maxWidth = width)
            )
            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = entry.value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A bar narrower than this cannot hold its own rank number, and reads as a rendering fault. */
private const val MinBar = 0.14f
private val BarHeight = 36.dp
