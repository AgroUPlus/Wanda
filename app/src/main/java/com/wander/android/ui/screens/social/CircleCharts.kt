package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.wander.android.data.sources.agro.AgroTasteMatrixEntry
import com.wander.android.data.sources.agro.StatEntry
import com.wander.android.ui.components.ConnectedToggleGroup
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.components.RankedList
import com.wander.android.ui.components.SegmentGap
import com.wander.android.ui.components.segmentedShape
import com.wander.android.ui.theme.listTitle
import com.wander.android.ui.theme.matchPercent
import com.wander.android.ui.theme.sectionTitle

private enum class CircleChart { ARTISTS, TRACKS }

/**
 * The circle's top artists or top tracks, one at a time behind a switch rather than as two cards
 * stacked one after the other.
 */
@Composable
internal fun CircleCharts(topArtists: List<StatEntry>, topTracks: List<StatEntry>) {
    var chart by rememberSaveable { mutableStateOf(if (topArtists.isNotEmpty()) CircleChart.ARTISTS else CircleChart.TRACKS) }
    Column {
        ConnectedToggleGroup(
            options = CircleChart.entries,
            selected = chart,
            label = { stringResource(if (it == CircleChart.ARTISTS) R.string.social_top_artists else R.string.social_top_tracks) },
            onSelect = { chart = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp)
        )
        RankedList(
            entries = (if (chart == CircleChart.ARTISTS) topArtists else topTracks).take(TOP_COUNT),
            countLabel = { it.toString() }
        )
    }
}

/** How closely each pair in the circle overlaps, as a segmented list of bars. */
internal fun LazyListScope.tasteCompatibilitySection(matrix: List<AgroTasteMatrixEntry>) {
    item(key = "taste_header") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
        ) {
            Icon(Icons.Rounded.Stars, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.social_taste_compatibility), style = MaterialTheme.typography.sectionTitle)
        }
    }
    items(count = matrix.size, key = { "taste_" + matrix[it].a + "_" + matrix[it].b }) { index ->
        TastePairRow(
            entry = matrix[index],
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (index == 0) 0.dp else SegmentGap),
            index = index,
            count = matrix.size
        )
    }
}

@Composable
private fun TastePairRow(entry: AgroTasteMatrixEntry, index: Int, count: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = segmentedShape(index, count), color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row {
                CuteAvatar(seed = entry.a, size = 36.dp, shape = PersonShape)
                CuteAvatar(seed = entry.b, size = 36.dp, shape = PersonShape, modifier = Modifier.offset(x = (-10).dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.circle_pair, entry.a, entry.b),
                    style = MaterialTheme.typography.listTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = { entry.score / 100f },
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(64.dp)) {
                Text(
                    text = stringResource(R.string.circle_match_percent, entry.score),
                    style = MaterialTheme.typography.matchPercent,
                    color = if (entry.score > 0) colors.primary else colors.outline
                )
                Text(
                    text = stringResource(R.string.circle_match_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private const val TOP_COUNT = 5
