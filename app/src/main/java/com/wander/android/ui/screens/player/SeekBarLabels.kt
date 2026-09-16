package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * How the seek bar says what time it is: the two end labels, and the bubble that rides the thumb.
 *
 * Split out of `PlayerSeekBar` when that file passed the length where the slider itself — the
 * scrubbing state, the wave, the two label layouts — stopped being the only thing in it.
 */

/**
 * One end of the bar. Both layouts in `PlayerSeekBar` place two of these.
 *
 * [color] is explicit rather than always `onSurfaceVariant` because the inline layout sits inside
 * a container that sets its own content colour — the lyrics screen's black pill — and a label that
 * insisted on a theme role would have been near-invisible on it.
 */
@Composable
internal fun TimeLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
    )
}

/**
 * The time bubble that rides above the thumb while a finger is on it.
 *
 * A function of its own rather than written inline, and not for tidiness: inline, it sat lexically
 * inside the seek bar's `Column`, so `ColumnScope.AnimatedVisibility` won overload resolution — and
 * was then rejected, because the slider's `thumb` lambda is not a `ColumnScope`. Out here there is
 * no such receiver in scope and the ordinary overload is chosen. Forcing the receiver instead would
 * have compiled and animated the bubble as a column child, which is not what it is.
 */
@Composable
internal fun ScrubTooltip(visible: Boolean, text: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.8f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f),
        modifier = Modifier.layout { measurable, _ ->
            val placeable = measurable.measure(Constraints())
            layout(0, 0) {
                placeable.placeRelative(
                    x = -placeable.width / 2,
                    y = -placeable.height - 24.dp.roundToPx()
                )
            }
        }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

internal fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
