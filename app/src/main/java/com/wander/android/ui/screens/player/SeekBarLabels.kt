package com.wander.android.ui.screens.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * How the seek bar says what time it is: the two end labels and the time format.
 * The bubble that rides the thumb is in `ScrubBubble.kt`.
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
