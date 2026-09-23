package com.wander.android.ui.screens.replay

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.ui.theme.LocalReducedMotion

/**
 * The cards that are mostly one big number: minutes, the shape of the year, hours, streaks and
 * where the year stood against everyone else's.
 *
 * Grouped by what they *are* rather than one file each — they share the counter and the bar, and
 * splitting them further would mean six files of twenty lines and two shared helpers somewhere
 * else.
 */

/** A number that counts up to itself, or simply is itself when motion is off. */
@Composable
private fun CountUpNumber(target: Long, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    var started by remember { mutableIntStateOf(0) }

    LaunchedEffect(target) { started = target.toInt() }
    val shown by animateIntAsState(
        targetValue = if (reduced) target.toInt() else started,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "replayCountUp"
    )

    Text(
        text = "%,d".format(if (reduced) target else shown.toLong()),
        style = MaterialTheme.typography.displayLargeEmphasized.copy(fontWeight = FontWeight.Black),
        color = LocalContentColor.current,
        textAlign = TextAlign.Center,
        maxLines = 1,
        // Poster-sized for a four-digit year of listening, stepping down so a six-digit one still
        // fits on one line.
        autoSize = TextAutoSize.StepBased(minFontSize = MinNumberSize, maxFontSize = MaxNumberSize),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun ReplayMinutesCard(card: ReplayCard.Minutes) {
    ReplayCardFrame(
        kicker = stringResource(R.string.replay_minutes_kicker),
        headline = stringResource(R.string.replay_minutes_headline)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CountUpNumber(card.minutes)
            Text(
                text = pluralStringResource(
                    R.plurals.replay_minutes_detail,
                    card.plays.toInt(),
                    card.plays
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = replayMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Twelve bars, rising together, with the heaviest month called out underneath. */
@Composable
internal fun ReplayShapeCard(card: ReplayCard.Shape) {
    val peak = card.byMonth.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    ReplayCardFrame(
        kicker = stringResource(R.string.replay_shape_kicker),
        headline = stringResource(R.string.replay_shape_headline)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(BarLabelGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(ChartHeight),
                horizontalArrangement = Arrangement.spacedBy(BarGap),
                verticalAlignment = Alignment.Bottom
            ) {
                card.byMonth.forEachIndexed { index, plays ->
                    GrowingBar(
                        fraction = plays.toFloat() / peak,
                        index = index,
                        highlighted = index == card.peakMonth,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.replay_shape_peak,
                    stringResource(MonthNames[card.peakMonth])
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = replayMuted
            )
        }
    }
}

/** The clock: twenty-four bars around the day, with the busiest hour named. */
@Composable
internal fun ReplayHoursCard(card: ReplayCard.Hours) {
    val peak = card.byHour.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    ReplayCardFrame(
        kicker = stringResource(R.string.replay_hours_kicker),
        headline = stringResource(R.string.replay_hours_headline, formatHour(card.peakHour))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ChartHeight),
            horizontalArrangement = Arrangement.spacedBy(HourBarGap),
            verticalAlignment = Alignment.Bottom
        ) {
            card.byHour.forEachIndexed { hour, plays ->
                GrowingBar(
                    fraction = plays.toFloat() / peak,
                    index = hour,
                    highlighted = hour == card.peakHour,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun ReplayStreakCard(card: ReplayCard.Streak) {
    ReplayCardFrame(
        kicker = stringResource(R.string.replay_streak_kicker),
        headline = pluralStringResource(
            R.plurals.replay_streak_headline,
            card.longestStreakDays,
            card.longestStreakDays
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(BarLabelGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReplayBadge(text = "%,d".format(card.longestStreakDays), shape = MaterialShapes.Burst)
            Text(
                text = pluralStringResource(
                    R.plurals.replay_streak_detail,
                    card.activeDays,
                    card.activeDays
                ),
                style = MaterialTheme.typography.titleMedium,
                color = replayMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Where the year sat among the server's other listeners.
 *
 * Only ever drawn for a standing the server actually computed — a small server is told nothing
 * rather than "top 100%", and the card is absent instead.
 */
@Composable
internal fun ReplayChartsCard(card: ReplayCard.Charts) {
    ReplayCardFrame(
        kicker = stringResource(R.string.replay_charts_kicker),
        headline = stringResource(R.string.replay_charts_headline, card.percentile)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(BarLabelGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReplayBadge(text = "${card.percentile}%", shape = MaterialShapes.SoftBoom)
            Text(
                text = pluralStringResource(
                    R.plurals.replay_charts_detail,
                    card.cohortSize,
                    card.cohortSize
                ),
                style = MaterialTheme.typography.titleMedium,
                color = replayMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** One bar, growing into place on a stagger so a chart assembles rather than appearing. */
@Composable
private fun GrowingBar(
    fraction: Float,
    index: Int,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val scale = rememberBarGrowth(index)
    val content = LocalContentColor.current
    val colour = if (highlighted) content else content.copy(alpha = QuietBarAlpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight * fraction.coerceIn(MinBarFraction, 1f) * scale)
            .clip(CircleShape)
            .background(colour)
    )
}

private val MonthNames = listOf(
    R.string.replay_month_january, R.string.replay_month_february, R.string.replay_month_march,
    R.string.replay_month_april, R.string.replay_month_may, R.string.replay_month_june,
    R.string.replay_month_july, R.string.replay_month_august, R.string.replay_month_september,
    R.string.replay_month_october, R.string.replay_month_november, R.string.replay_month_december
)

private val ChartHeight = 180.dp
private val BarGap = 6.dp
private val HourBarGap = 2.dp
private val BarLabelGap = 16.dp
private val MinNumberSize = 40.sp
private val MaxNumberSize = 120.sp
private const val QuietBarAlpha = 0.32f

/** A bar with nothing in it is still drawn, so the shape of the year has twelve months in it. */
private const val MinBarFraction = 0.02f
