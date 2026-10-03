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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.repository.ListeningReport
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.theme.sectionTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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
    onBack: () -> Unit,
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
                onBack = onBack,
                topInset = contentPadding.headerInset()
            )
        }

        state.error?.let { message ->
            item(key = "error") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = message, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::retry, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.common_try_again))
                        }
                    }
                }
            }
        }

        if (state.report == null && state.isLoading) {
            item(key = "loading") {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                    LoadingIndicator()
                }
            }
        }

        state.report?.let { report ->
            reportBody(
                report = report,
                windowLabel = report.window.label(now, zone),
                dayLabel = { millis -> formatDay(millis, zone) },
                // The window's bound is exclusive, and for the open-ended "all" window it is simply now.
                lastDayMillis = if (report.window.isLatest) now else report.window.bounds(now, zone).last - DAY_MS,
                onEarlier = viewModel::showEarlier,
                onLater = viewModel::showLater
            )
        }
    }
}

private fun LazyListScope.reportBody(
    report: ListeningReport,
    windowLabel: String,
    dayLabel: (Long) -> String,
    lastDayMillis: Long,
    onEarlier: () -> Unit,
    onLater: () -> Unit
) {
    item(key = "summary") { SummaryCard(report, windowLabel, onEarlier, onLater) }

    item(key = "facts_header") { StatsSectionTitle(stringResource(R.string.stats_quick_facts)) }
    item(key = "facts") { QuickFactTiles(report) }
    if (report.busiestDay != null || report.artists != null) {
        item(key = "more_facts") { MoreFacts(report, dayLabel) }
    }

    val stats = report.stats
    item(key = "by_day") {
        val count = stats.byDay.size
        ChartCard(title = stringResource(R.string.stats_last_14_days)) {
            DayBars(
                values = stats.byDay,
                firstLabel = dayLabel(lastDayMillis - (count - 1).coerceAtLeast(0) * DAY_MS),
                lastLabel = if (report.window.isLatest) stringResource(R.string.stats_today) else dayLabel(lastDayMillis),
                lastIsToday = report.window.isLatest
            )
        }
    }
    item(key = "by_hour") {
        ChartCard(title = stringResource(R.string.stats_hour), subtitle = stringResource(R.string.stats_your_own_clock)) {
            HourBars(stats.byHour)
        }
    }

    if (stats.byDevice.isNotEmpty()) {
        item(key = "devices_header") { StatsSectionTitle(stringResource(R.string.stats_by_device)) }
        item(key = "devices") { DeviceList(stats.byDevice) }
    }

    item(key = "top_lists") { TopLists(stats) }
}

@Composable
private fun StatsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.sectionTitle,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)
    )
}

private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun formatDay(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(DayMonth)

private val DAY_MS = TimeUnit.DAYS.toMillis(1)
