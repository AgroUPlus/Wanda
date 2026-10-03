package com.wander.android.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.wander.android.R
import com.wander.android.data.repository.ListeningReport
import com.wander.android.ui.components.SunnyShape
import com.wander.android.ui.theme.displayStatHuge
import com.wander.android.ui.theme.displayStatMid
import com.wander.android.ui.theme.displayStatTime

/**
 * The one figure the window is summed up by, how it moved, and what the figures cover: which dates
 * (with a step either side when the source can page) and which devices.
 *
 * The arrows are hidden rather than disabled when there is nothing to page through — an Agro account
 * answers for a period rather than a window, and a greyed arrow invites a tap that does nothing.
 */
@Composable
internal fun SummaryCard(
    report: ListeningReport,
    windowLabel: String,
    onEarlier: () -> Unit,
    onLater: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = report.songsPlayed.value.toString(),
                    style = MaterialTheme.typography.displayStatHuge,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    report.songsPlayed.changePercent?.let { change ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            ChangeLabel(change, MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.stats_vs_previous_period),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.stats_songs_played),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaLine(Icons.Rounded.CalendarToday, windowLabel, Modifier.weight(1f))
                    if (report.isPageable) {
                        IconButton(onClick = onEarlier) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.stats_earlier))
                        }
                        // Present but inert at the newest window, so the row does not reflow on reaching the present.
                        IconButton(onClick = onLater, enabled = !report.window.isLatest) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.stats_later))
                        }
                    }
                }
                MetaLine(
                    Icons.Rounded.Devices,
                    stringResource(if (report.isFleetWide) R.string.stats_scope_fleet else R.string.stats_scope_device)
                )
            }
        }
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Total time and the daily average, as a pair of square tiles — the plain one for the duration, the
 * sunny one for the average.
 */
@Composable
internal fun QuickFactTiles(report: ListeningReport) {
    val colors = MaterialTheme.colorScheme
    val label = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 1.25.em)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(colors.primaryContainer, RoundedCornerShape(32.dp))
                .padding(20.dp)
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(26.dp))
            Column {
                Text(
                    text = formatListeningTime(report.listenedSeconds.value),
                    style = MaterialTheme.typography.displayStatTime,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(R.string.stats_total_listened_time), style = label, color = colors.onPrimaryContainer)
                    report.listenedSeconds.changePercent?.let {
                        ChangeLabel(it, MaterialTheme.typography.labelMedium, Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f).aspectRatio(1f).background(colors.tertiary, SunnyShape)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = report.playsPerDay?.value?.toString() ?: stringResource(R.string.stats_value_unknown),
                    style = MaterialTheme.typography.displayStatMid,
                    color = colors.onTertiary
                )
                Text(
                    text = stringResource(R.string.stats_plays_word) + "\n" + stringResource(R.string.stats_average_per_day),
                    style = label,
                    color = colors.onTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
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
internal fun ChangeLabel(changePercent: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val color = when {
        changePercent > 0 -> MaterialTheme.colorScheme.primary
        changePercent < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(if (changePercent > 0) R.string.stats_change_up else R.string.stats_change_down, changePercent),
        style = style,
        color = color,
        modifier = modifier
    )
}
