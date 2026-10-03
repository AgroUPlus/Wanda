package com.wander.android.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.ListeningReport
import com.wander.android.data.repository.Trend
import com.wander.android.ui.components.SegmentGap
import com.wander.android.ui.components.segmentedShape
import com.wander.android.ui.theme.listSupporting
import com.wander.android.ui.theme.listTitle

/**
 * The facts only the local history can answer — the busiest day and how many artists — under the
 * two tiles, as a short segmented list. Absent rather than zero when the source cannot say.
 */
@Composable
internal fun MoreFacts(report: ListeningReport, dayLabel: (Long) -> String) {
    val facts = buildList {
        report.busiestDay?.let { day ->
            add(
                Triple(
                    stringResource(R.string.stats_most_active_day),
                    stringResource(R.string.stats_busiest_day_value, day.plays.value.toString(), dayLabel(day.dayStartMillis)),
                    day.plays
                )
            )
        }
        report.artists?.let { add(Triple(stringResource(R.string.stats_artists), it.value.toString(), it)) }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        facts.forEachIndexed { index, (label, value, trend) ->
            FactRow(label, value, trend, index, facts.size)
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, trend: Trend, index: Int, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, segmentedShape(index, count))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.listSupporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.listTitle)
        trend.changePercent?.let {
            ChangeLabel(it, MaterialTheme.typography.labelMedium, Modifier.padding(start = 8.dp))
        }
    }
}
