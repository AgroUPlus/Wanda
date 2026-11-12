package com.wander.android.ui.screens.queue

import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.playPosition
import com.wander.android.core.playback.rememberPlaybackPosition
import java.util.Date

/**
 * Beside "Up next": how long the queue has left, and the clock time it runs out — what is left of
 * the playing track plus every upcoming one. The clock follows the phone's 12/24-hour setting.
 *
 * Only when every one of those durations is known; a queue with a stream or an untimed track in it
 * says where it is in the queue instead, rather than an end time that is wrong. It is the only
 * reader of the position in the drawer, sampled once a second, so only this line recomposes on it.
 */
@Composable
internal fun UpNextMeta(playerConnection: PlayerConnection, state: PlaybackState, upcoming: List<QueueEntry>) {
    if (state.queue.isEmpty()) return
    val position by rememberPlaybackPosition(playerConnection, intervalMs = POSITION_SAMPLE_MS)
    val context = LocalContext.current
    val clock = remember(context) { DateFormat.getTimeFormat(context) }

    val current = state.currentTrack
    val currentDuration = state.durationMs.takeIf { it > 0L } ?: current?.durationMs ?: 0L
    val timed = current != null && currentDuration > 0L && upcoming.all { it.track.durationMs > 0L }
    val text = if (timed) {
        val leftMs = (currentDuration - position.positionMs).coerceAtLeast(0L) +
            upcoming.sumOf { it.track.durationMs }
        val minutes = ((leftMs + HALF_MINUTE_MS) / MINUTE_MS).toInt()
        // Off the wall clock at every sample. Paused, there are no samples, so the end time holds
        // where it was until playback or the queue changes — never a second ticker for it.
        val endsAt = clock.format(Date(System.currentTimeMillis() + leftMs))
        pluralStringResource(R.plurals.queue_next_time_left, minutes, minutes, endsAt)
    } else {
        stringResource(R.string.queue_tracks, state.playPosition + 1, state.queue.size)
    }
    QueueMetaText(text)
}

/** Beside "History": how many tracks have played. */
@Composable
internal fun HistoryMeta(playedCount: Int) {
    QueueMetaText(pluralStringResource(R.plurals.queue_next_played_count, playedCount, playedCount))
}

@Composable
private fun QueueMetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Once a second: the line shows whole minutes, so anything faster would only cost wakeups. */
private const val POSITION_SAMPLE_MS = 1_000L
private const val MINUTE_MS = 60_000L
private const val HALF_MINUTE_MS = 30_000L
