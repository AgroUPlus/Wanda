package com.wander.android.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroStats
import com.wander.android.data.sources.agro.StatEntry
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.components.EmblemShape
import com.wander.android.ui.components.RankedList
import com.wander.android.ui.components.SegmentGap
import com.wander.android.ui.components.segmentedShape
import com.wander.android.ui.theme.listTitle

/**
 * Listening time per device, as a segmented list with each device's share of the total.
 *
 * Every row has the same generic icon: the sources name a device but do not say what kind it is,
 * and guessing "phone" from a name would be wrong often enough to mislead.
 */
@Composable
internal fun DeviceList(devices: List<StatEntry>) {
    val total = devices.sumOf { it.value }.coerceAtLeast(1L)
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap), modifier = Modifier.padding(horizontal = 16.dp)) {
        devices.forEachIndexed { index, device ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainer, segmentedShape(index, devices.size))
                    .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).background(colors.secondaryContainer, EmblemShape)
                ) {
                    Icon(Icons.Rounded.Devices, null, tint = colors.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.listTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatListeningTime(device.value),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { device.value.toFloat() / total },
                        color = colors.primary,
                        trackColor = colors.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
            }
        }
    }
}

private enum class TopListKind { ARTISTS, ALBUMS, TRACKS }

/** The three top-tens behind one switch, rather than three lists stacked down the screen. */
@Composable
internal fun TopLists(stats: AgroStats) {
    var kind by rememberSaveable { mutableStateOf(TopListKind.ARTISTS) }
    Column {
        ConnectedToggleGroup(
            options = TopListKind.entries,
            selected = kind,
            label = {
                stringResource(
                    when (it) {
                        TopListKind.ARTISTS -> R.string.stats_tab_artists
                        TopListKind.ALBUMS -> R.string.stats_tab_albums
                        TopListKind.TRACKS -> R.string.stats_tab_tracks
                    }
                )
            },
            onSelect = { kind = it },
            checkIconGap = 4.dp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
        )
        val entries = when (kind) {
            TopListKind.ARTISTS -> stats.topArtists
            TopListKind.ALBUMS -> stats.topAlbums
            TopListKind.TRACKS -> stats.topTracks
        }
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_nothing_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        } else {
            RankedList(
                entries = entries,
                countLabel = { stringResource(R.string.stats_plays_count, it.toString()) },
                compact = true
            )
        }
    }
}
