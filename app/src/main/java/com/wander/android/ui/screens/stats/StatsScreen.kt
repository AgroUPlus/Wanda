package com.wander.android.ui.screens.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.sources.agro.AgroStats
import com.wander.android.data.sources.agro.StatsPeriod

/**
 * What you have been listening to.
 *
 * With an Agro server paired the figures cover every device on the account; without one they cover
 * this handset. The difference is stated on screen rather than left to be inferred — the same
 * number means two quite different things in the two cases.
 */
@Composable
fun StatsScreen(
    contentPadding: PaddingValues,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "title") {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text(text = "Listening", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = if (state.isFleetWide) {
                        "Every device on your Agro account"
                    } else {
                        "This device only — pair with Agro to combine your devices"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "period") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                StatsPeriod.entries.forEach { period ->
                    ToggleButton(
                        checked = state.period == period,
                        onCheckedChange = { viewModel.setPeriod(period) }
                    ) { Text(period.label) }
                }
            }
        }

        state.error?.let { message ->
            item(key = "error") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = message, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::retry, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                    }
                }
            }
        }

        if (state.stats == null && state.isLoading) {
            item(key = "loading") {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                ) {
                    LoadingIndicator()
                }
            }
        }

        state.stats?.let { stats -> statsBody(stats) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.statsBody(stats: AgroStats) {
    item(key = "totals") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            StatTile("Today", formatListeningTime(stats.secondsToday))
            StatTile("This week", formatListeningTime(stats.secondsWeek))
            StatTile("Total", formatListeningTime(stats.secondsTotal))
            StatTile("Plays", stats.playCount.toString())
            StatTile("Streak", "${stats.streakDays}d")
        }
    }

    item(key = "by_day") {
        ChartCard(title = "Last 14 days") { BarChart(stats.byDay) }
    }

    item(key = "by_hour") {
        ChartCard(
            title = "By hour",
            subtitle = "UTC, so it will be offset from your clock"
        ) { BarChart(stats.byHour) }
    }

    if (stats.byDevice.isNotEmpty()) {
        item(key = "devices_header") { SectionHeader("By device") }
        item(key = "devices") {
            TopList(entries = stats.byDevice, valueLabel = ::formatListeningTime)
        }
    }

    item(key = "artists_header") { SectionHeader("Top artists") }
    item(key = "artists") { TopList(entries = stats.topArtists, valueLabel = { "$it plays" }) }

    item(key = "albums_header") { SectionHeader("Top albums") }
    item(key = "albums") { TopList(entries = stats.topAlbums, valueLabel = { "$it plays" }) }

    item(key = "tracks_header") { SectionHeader("Top tracks") }
    item(key = "tracks") { TopList(entries = stats.topTracks, valueLabel = { "$it plays" }) }
}

@Composable
private fun StatTile(label: String, value: String) {
    Card(modifier = Modifier.padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}
