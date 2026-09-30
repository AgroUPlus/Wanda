package com.wander.android.ui.screens.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The date break between one day of plays and the next — a wavy M3 Expressive rule with the day
 * named in the gap, `"⌇⌇⌇ 2 November ⌇⌇⌇"`-shaped.
 *
 * A drawn wave rather than a plain `HorizontalDivider`: [HistoryScreen] groups by day, not by any
 * hierarchy worth a bold section title, and a Stats-style flat section heading reads as a heading
 * for what follows rather than a seam between two things that are both history. The line breaks
 * for the text rather than running under it, the same way a stitched seam does.
 */
@Composable
internal fun HistoryDateDivider(dayMillis: Long, zone: ZoneId, modifier: Modifier = Modifier) {
    val today = stringResource(R.string.common_today)
    val yesterday = stringResource(R.string.common_yesterday)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        WavyRule(modifier = Modifier.weight(1f))
        Text(
            text = dayLabel(dayMillis, zone, today, yesterday),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        WavyRule(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WavyRule(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.height(WaveHeight)) {
        val amplitude = size.height / 2.4f
        val midY = size.height / 2
        val wavelength = WaveLengthDp.toPx()
        val path = Path().apply {
            moveTo(0f, midY)
            var x = 0f
            var crestUp = true
            while (x < size.width) {
                val nextX = (x + wavelength / 2).coerceAtMost(size.width)
                val midX = (x + nextX) / 2
                quadraticTo(midX, midY + if (crestUp) -amplitude else amplitude, nextX, midY)
                x = nextX
                crestUp = !crestUp
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = StrokeWidthDp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private val WaveHeight = 10.dp
private val WaveLengthDp = 14.dp
private val StrokeWidthDp = 2.dp

private val TodayZone: (ZoneId) -> LocalDate = { LocalDate.now(it) }
private val FullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val FullDateWithYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

private fun dayLabel(millis: Long, zone: ZoneId, todayLabel: String, yesterdayLabel: String): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = TodayZone(zone)
    return when {
        date == today -> todayLabel
        date == today.minusDays(1) -> yesterdayLabel
        date.year == today.year -> date.format(FullDate)
        else -> date.format(FullDateWithYear)
    }
}

/** Whether two epoch-millis timestamps fall on the same calendar day in [zone]. */
internal fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
    Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
