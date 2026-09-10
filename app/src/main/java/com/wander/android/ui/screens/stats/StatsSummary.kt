package com.wander.android.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.repository.ListeningReport
import com.wander.android.data.repository.Trend

/**
 * The dates the figures below cover, with a step either side.
 *
 * The arrows are hidden rather than disabled when there is nothing to page through — an all-time
 * window has no neighbours, and an Agro account answers for a period rather than for a window. A
 * greyed arrow invites a tap and then does nothing, which reads as the screen being broken.
 */
@Composable
internal fun WindowNavigator(
    label: String,
    isPageable: Boolean,
    isLatest: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StatsGutter, vertical = 4.dp)
    ) {
        if (isPageable) {
            FilledTonalIconButton(onClick = onEarlier) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Earlier"
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        if (isPageable) {
            // Present but inert at the newest window, so the label stays centred rather than
            // sliding sideways every time the user reaches the present.
            FilledTonalIconButton(onClick = onLater, enabled = !isLatest) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Later"
                )
            }
        }
    }
}

/** The one figure the window is summed up by, with how it moved. */
@Composable
internal fun HeadlineFigure(
    songsPlayed: Trend,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StatsGutter, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = songsPlayed.value.toString(),
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = "Songs played",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        songsPlayed.changePercent?.let { change ->
            Column(horizontalAlignment = Alignment.End) {
                ChangeLabel(change, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "vs previous period",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The rest of the window in four numbers.
 *
 * Two fixed columns rather than a grid: there are at most four of these and the count is decided
 * by what the source can answer, so a layout that reflows buys nothing over a pair of rows.
 */
@Composable
internal fun QuickFacts(
    report: ListeningReport,
    dayLabel: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val facts = buildList {
        add(Fact("Total listened time", formatListeningTime(report.listenedSeconds.value), report.listenedSeconds.changePercent))
        add(Fact("Average per day", "${report.playsPerDay.value} plays", report.playsPerDay.changePercent))
        report.busiestDay?.let { day ->
            add(
                Fact(
                    label = "Most active day",
                    value = "${day.plays.value} plays · ${dayLabel(day.dayStartMillis)}",
                    changePercent = day.plays.changePercent
                )
            )
        }
        report.artists?.let { add(Fact("Artists", it.value.toString(), it.changePercent)) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StatsGutter, vertical = 4.dp)
    ) {
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { fact ->
                    FactTile(fact, modifier = Modifier.weight(1f))
                }
                // Keeps a lone tile in a row of two the width of the tiles above it rather than
                // stretching it across the screen.
                if (row.size == 1) Column(modifier = Modifier.weight(1f)) {}
            }
        }
    }
}

private data class Fact(val label: String, val value: String, val changePercent: Int?)

@Composable
private fun FactTile(fact: Fact, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = fact.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = fact.value,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                fact.changePercent?.let { change ->
                    ChangeLabel(
                        change,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * `+15%` or `-8%`.
 *
 * Coloured by direction, and the sign is written out as well — colour alone is not a difference
 * everyone can see, and the sign is what the figure actually means.
 */
@Composable
private fun ChangeLabel(
    changePercent: Int,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val color: Color = when {
        changePercent > 0 -> MaterialTheme.colorScheme.primary
        changePercent < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = if (changePercent > 0) "+$changePercent%" else "$changePercent%",
        style = style,
        color = color,
        modifier = modifier
    )
}
