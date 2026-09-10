package com.wander.android.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.repository.ListeningReport
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What you have been listening to.
 *
 * With an Agro server paired the figures cover every device on the account; without one they cover
 * this handset. The difference is stated on screen rather than left to be inferred — the same
 * number means two quite different things in the two cases — and it also decides what the screen
 * can offer: only the local history can be paged window by window or compared against the window
 * before it, so those controls appear only when they would work. See [ListeningReport].
 */
@Composable
fun StatsScreen(
    contentPadding: PaddingValues,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(state.window) { System.currentTimeMillis() }

    LazyColumn(
        contentPadding = contentPadding.listInset(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "hero") {
            StatsHero(
                topSong = state.report?.topSong,
                period = state.window.period,
                onPeriod = viewModel::setPeriod,
                topInset = contentPadding.headerInset()
            )
        }

        item(key = "scope") {
            Text(
                text = if (state.isFleetWide) {
                    "Every device on your Agro account"
                } else {
                    "This device only — pair with Agro to combine your devices"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = StatsGutter, vertical = 8.dp)
            )
        }

        state.error?.let { message ->
            item(key = "error") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = StatsGutter, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = message, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::retry, shapes = ButtonDefaults.shapes()) {
                            Text("Try again")
                        }
                    }
                }
            }
        }

        if (state.report == null && state.isLoading) {
            item(key = "loading") {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp)
                ) {
                    LoadingIndicator()
                }
            }
        }

        state.report?.let { report ->
            reportBody(
                report = report,
                windowLabel = report.window.label(now, zone),
                onEarlier = viewModel::showEarlier,
                onLater = viewModel::showLater,
                dayLabel = { millis -> formatDay(millis, zone) }
            )
        }
    }
}

private fun LazyListScope.reportBody(
    report: ListeningReport,
    windowLabel: String,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    dayLabel: (Long) -> String
) {
    item(key = "window") {
        WindowNavigator(
            label = windowLabel,
            isPageable = report.isPageable,
            isLatest = report.window.isLatest,
            onEarlier = onEarlier,
            onLater = onLater
        )
    }

    item(key = "headline") { HeadlineFigure(report.songsPlayed) }

    item(key = "facts_header") { SectionHeader("Quick facts") }
    item(key = "facts") { QuickFacts(report, dayLabel) }

    val stats = report.stats

    item(key = "by_day") {
        ChartCard(title = "Last 14 days") { BarChart(stats.byDay) }
    }

    item(key = "by_hour") {
        ChartCard(title = "By hour", subtitle = "Your own clock") { BarChart(stats.byHour) }
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
private fun ChartCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = StatsGutter, vertical = 6.dp)
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
        modifier = Modifier.padding(start = StatsGutter, top = 20.dp, bottom = 8.dp)
    )
}

private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun formatDay(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(DayMonth)
