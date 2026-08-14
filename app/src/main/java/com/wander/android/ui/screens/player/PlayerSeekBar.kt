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
    modifier: Modifier = Modifier
) {
    val position by rememberPlaybackPosition(playerConnection, intervalMs = 250L)
    PlayerSeekBarInternal(
        positionMs = position.positionMs,
        durationMs = durationMs,
        onSeek = onSeek,
        modifier = modifier
    )
}

@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerSeekBarInternal(positionMs, durationMs, onSeek, modifier)
}

@Composable
private fun PlayerSeekBarInternal(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
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
                text = formatTime((fraction * durationMs).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (durationMs > 0L) formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
