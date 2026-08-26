package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.rememberPlaybackPosition
import com.wander.android.ui.components.LiveChip
import java.util.Locale

/**
 * Expressive wavy slider. While the user drags, the local value wins so the thumb tracks the
 * finger instead of fighting the periodic position updates.
 */
@Composable
fun PlayerSeekBar(
    playerConnection: PlayerConnection,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false
) {
    val position by rememberPlaybackPosition(playerConnection, intervalMs = 250L)
    PlayerSeekBarInternal(
        positionMs = position.positionMs,
        durationMs = durationMs,
        onSeek = onSeek,
        modifier = modifier,
        isLive = isLive
    )
}

@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false
) {
    PlayerSeekBarInternal(positionMs, durationMs, onSeek, modifier, isLive)
}

@Composable
private fun PlayerSeekBarInternal(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false
) {
    var scrubbing by remember { mutableFloatStateOf(-1f) }
    val fraction = if (scrubbing >= 0f) {
        scrubbing
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                if (durationMs > 0L) onSeek((scrubbing * durationMs).toLong())
                scrubbing = -1f
            },
            enabled = durationMs > 0L
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isLive) formatTime(positionMs) else formatTime((fraction * durationMs).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            when {
                // A stream with no end has no total to count towards, and `--:--` reads as
                // metadata that failed to load rather than as "this is happening right now".
                isLive -> LiveChip()
                durationMs > 0L -> Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Text(
                    text = "--:--",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
