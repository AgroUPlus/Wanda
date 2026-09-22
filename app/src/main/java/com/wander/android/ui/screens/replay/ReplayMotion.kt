package com.wander.android.ui.screens.replay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wander.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The bits of motion and formatting the cards share.
 *
 * Kept together so a card file is only ever its own layout, and so there is one place that knows
 * how the story behaves when motion is turned off.
 */

/**
 * A bar's height multiplier, growing from nothing on a stagger.
 *
 * The stagger is a `delay`, which is not something `MaterialTheme.motionScheme` has a spec for —
 * the same reason `rememberShelfEntranceScale` reads [LocalReducedMotion] directly. The spring
 * itself does come from the scheme, so with reduced motion on it snaps along with everything else;
 * the delay is skipped outright so a chart is simply *there*.
 */
@Composable
internal fun rememberBarGrowth(index: Int): Float {
    if (LocalReducedMotion.current) return 1f

    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(MaxStaggeredBars) * BarStaggerMs)
        grown = true
    }

    val scale by animateFloatAsState(
        targetValue = if (grown) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "replayBarGrowth"
    )
    return scale
}

/**
 * An hour of the day as the reader's locale writes it — "2:00 PM", "14:00".
 *
 * A localised style rather than a pattern: a fixed `"h a"` would print "2 PM" to somebody whose
 * locale has never used an am/pm clock, which is precisely the reader this card is telling a small
 * joke to about being up at two.
 */
internal fun formatHour(hour: Int): String =
    LocalTime.of(hour.coerceIn(0, LastHour), 0)
        .format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
        )

private const val BarStaggerMs = 24L

/** Past this the cascade stops adding delay, so a 24-bar clock does not take a second to build. */
private const val MaxStaggeredBars = 12
private const val LastHour = 23
