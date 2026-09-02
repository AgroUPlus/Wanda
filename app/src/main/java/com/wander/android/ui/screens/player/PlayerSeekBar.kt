package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rememberHaptics
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
    val haptics = rememberHaptics()
    val fraction = if (scrubbing >= 0f) {
        scrubbing
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    // A broadcast gets the chip alone, centred where the slider would have been.
    //
    // Nothing else on this row means anything for a livestream. There is no track to scrub — an
    // HLS live window reports a duration, the hour or so the broadcaster keeps available, so the
    // thumb used to be draggable; dragging it back and returning to the edge asks for segments
    // that have rolled out of the window and the stream dies with a source error. And an elapsed
    // count is a stopwatch on the listener, not a position in anything.
    //
    // The box keeps the height the slider and its labels occupied, so the artwork and the
    // transport controls above and below it do not shift when a live item starts.
    if (isLive) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(LiveRowHeight),
            contentAlignment = Alignment.Center
        ) {
            LiveChip()
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                // On release only, never while dragging. A tick per pixel of travel is the
                // definition of overdoing it; one on landing tells you the seek was taken.
                if (durationMs > 0L) {
                    haptics.settled()
                    onSeek((scrubbing * durationMs).toLong())
                }
                scrubbing = -1f
            },
            enabled = durationMs > 0L
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                // The position, not the fraction multiplied back out by the duration.
                //
                // Those agree whenever the duration is known and disagree completely when it is
                // not: an unknown duration pins `fraction` at zero, so this read `0:00` for a
                // whole track whose position the player was reporting correctly the entire time.
                // While a finger is on the thumb the fraction is what the user is choosing, and
                // that is the one case where it leads.
                text = formatTime(
                    if (scrubbing >= 0f) (scrubbing * durationMs).toLong() else positionMs
                ),
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

/**
 * The height a slider plus its label row occupies, so swapping one for the chip does not move
 * everything around it. Material's slider is 48.dp of touch target; the labels add the rest.
 */
private val LiveRowHeight = 68.dp

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
