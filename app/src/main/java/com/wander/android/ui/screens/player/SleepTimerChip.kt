package com.wander.android.ui.screens.player

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.SleepTimerState
import kotlinx.coroutines.delay

/**
 * A small badge under the player's top bar while a sleep timer is set: the time left on a
 * countdown, or "until the end" when playback stops at the end of the item. Nothing at all when
 * the timer is off.
 *
 * Drawn in the same fixed scrim as the other controls floating over the player (see
 * [playerOverlayButtonColors]), so it reads the same over artwork and over a plain surface.
 */
@Composable
internal fun SleepTimerChip(state: SleepTimerState, modifier: Modifier = Modifier) {
    val label = when (state) {
        SleepTimerState.Off -> return
        SleepTimerState.EndOfItem -> stringResource(R.string.sleep_timer_chip_until_end)
        is SleepTimerState.Countdown -> rememberTimeLeft(state)
    }
    val colors = playerOverlayButtonColors()
    Surface(
        color = colors.containerColor,
        contentColor = colors.contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.padding(top = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = stringResource(R.string.sleep_timer_chip_description),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * The countdown's time left as `m:ss`, ticking once a second — and only while something showing
 * it is on screen; nothing counts down otherwise.
 */
@Composable
internal fun rememberTimeLeft(state: SleepTimerState.Countdown): String {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(TICK_MS)
        }
    }
    return DateUtils.formatElapsedTime(((state.endsAtElapsedMs - now) / MS_PER_SECOND).coerceAtLeast(0L))
}

private const val TICK_MS = 1_000L
private const val MS_PER_SECOND = 1_000L
