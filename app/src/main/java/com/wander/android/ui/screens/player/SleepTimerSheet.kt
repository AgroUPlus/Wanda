package com.wander.android.ui.screens.player

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.SleepTimerState
import com.wander.android.ui.components.WandaSheet
import kotlinx.coroutines.delay

private val Durations = listOf(15, 30, 45, 60)

/**
 * Picks when playback stops. "End of episode" is the option podcast listeners actually use — a
 * fixed countdown cuts a conversation mid-sentence — so it is named for episodes when one plays.
 */
@Composable
internal fun SleepTimerSheet(
    state: SleepTimerState,
    isEpisode: Boolean,
    onCountdown: (minutes: Int) -> Unit,
    onEndOfItem: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    WandaSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (state is SleepTimerState.Countdown) Remaining(state)

            Durations.forEach { minutes ->
                TimerOption(Icons.Rounded.Timer, pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes)) {
                    onCountdown(minutes)
                    onDismiss()
                }
            }
            TimerOption(
                icon = Icons.Rounded.VerticalAlignBottom,
                label = stringResource(if (isEpisode) R.string.sleep_timer_end_of_episode else R.string.sleep_timer_end_of_track),
                selected = state == SleepTimerState.EndOfItem
            ) {
                onEndOfItem()
                onDismiss()
            }
            if (state != SleepTimerState.Off) {
                TimerOption(Icons.Rounded.BedtimeOff, stringResource(R.string.sleep_timer_off)) {
                    onCancel()
                    onDismiss()
                }
            }
        }
    }
}

/** Ticks once a second, and only while the sheet is open — nothing counts down on screen otherwise. */
@Composable
private fun Remaining(state: SleepTimerState.Countdown) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val left = DateUtils.formatElapsedTime(((state.endsAtElapsedMs - now) / 1_000L).coerceAtLeast(0L))
    ListItem(
        headlineContent = { Text(stringResource(R.string.sleep_timer_remaining, left)) },
        leadingContent = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun TimerOption(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = if (selected) {
            ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary, leadingIconColor = MaterialTheme.colorScheme.primary)
        } else {
            ListItemDefaults.colors()
        },
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp)
    )
}
