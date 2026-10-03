package com.wander.android.ui.screens.stats

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.ui.theme.extraColors
import com.wander.android.ui.theme.sectionTitle
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A chart in its own card, titled like a section. */
@Composable
internal fun ChartCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(20.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.sectionTitle)
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            content()
        }
    }
}

/**
 * Plays per day, oldest first. The last bar is today's when the window reaches the present, and
 * is the one drawn in primary; a quiet day still gets a stub so the row reads as fourteen days.
 */
@Composable
internal fun DayBars(values: List<Long>, firstLabel: String, lastLabel: String, lastIsToday: Boolean) {
    if (values.isEmpty()) return
    Bars(
        values = values,
        height = 120.dp,
        gap = 6.dp,
        corner = 6.dp,
        minBar = 6.dp,
        highlight = if (lastIsToday) values.lastIndex else -1
    )
    AxisRow(listOf(firstLabel, lastLabel), spread = true)
}

/** Plays by hour of the day, with the busiest hour in primary. */
@Composable
internal fun HourBars(values: List<Long>) {
    if (values.isEmpty()) return
    val peak = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 } ?: -1
    Bars(values = values, height = 110.dp, gap = 3.dp, corner = 4.dp, minBar = 4.dp, highlight = peak)
    val locale = LocalConfiguration.current.locales[0]
    val labels = remember(locale) {
        // "j" asks for the locale's own hour format, so this reads "6 am" or "06" as the user expects.
        val format = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "j"), locale)
        listOf(0, 6, 12, 18).map { LocalTime.of(it, 0).format(format) }
    }
    AxisRow(labels, spread = false)
}

@Composable
private fun Bars(values: List<Long>, height: Dp, gap: Dp, corner: Dp, minBar: Dp, highlight: Int) {
    val colors = MaterialTheme.colorScheme
    val secondary = MaterialTheme.extraColors.rankBarSecondary
    val top = values.max().coerceAtLeast(1L)
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth().height(height)
    ) {
        values.forEachIndexed { index, value ->
            val color: Color = when {
                index == highlight -> colors.primary
                value > 0 -> secondary
                else -> colors.surfaceContainerHighest
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((height * (value.toFloat() / top)).coerceAtLeast(minBar))
                    .background(color, RoundedCornerShape(corner))
            )
        }
    }
}

/** Two labels at the ends, or several in equal columns. */
@Composable
private fun AxisRow(labels: List<String>, spread: Boolean) {
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    val color = MaterialTheme.colorScheme.outline
    Row(
        horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = style,
                color = color,
                textAlign = TextAlign.Start,
                modifier = if (spread) Modifier else Modifier.weight(1f)
            )
        }
    }
}

/** Seconds as something a person reads: `3h 12m`, or `12m` when there are no hours. */
internal fun formatListeningTime(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

