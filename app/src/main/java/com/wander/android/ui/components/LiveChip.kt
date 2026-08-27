package com.wander.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * "LIVE" — shown where a recording would print its length.
 *
 * A livestream has no duration to scrub over, so the seek bar's total-time label has nothing to
 * say. It used to read `--:--`, which looks like metadata that failed to load rather than like a
 * stream that genuinely has no end.
 *
 * The dot breathes rather than blinking. A hard on/off draws the eye away from the artwork every
 * second; a slow opacity ramp reads as "still going" without competing for attention.
 *
 * [pulsing] turns that off, and list rows pass false. One breathing dot next to the thing you are
 * listening to reads as "this is happening now"; a screenful of them independently breathing in a
 * scrolling list is just noise, and each one is an animation running behind a row nobody is
 * looking at.
 */
@Composable
internal fun LiveChip(modifier: Modifier = Modifier, pulsing: Boolean = true) {
    val dotAlpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "live-pulse")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "live-dot"
        )
        animated
    } else {
        1f
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { alpha = dotAlpha }
                    .clip(CircleShape)
                    .background(ListeningGreen)
            )
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelMedium,
                color = ListeningGreen
            )
        }
    }
}
