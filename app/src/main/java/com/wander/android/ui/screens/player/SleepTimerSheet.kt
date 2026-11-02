package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.SleepTimerState
import com.wander.android.ui.components.WandaSheet
import kotlin.math.roundToInt

private val Durations = listOf(15, 30, 45, 60)

/** What the custom picker offers: five minutes to three hours, in fives. */
private const val CUSTOM_MIN = 5
private const val CUSTOM_MAX = 180
private const val CUSTOM_STEP = 5
private const val CUSTOM_DEFAULT = 90

/** How much "+5 minutes" adds to a running countdown. */
private const val EXTEND_MINUTES = 5

/** What the sheet can ask of the timer. Built once by the view model, so the screen passes one value. */
internal class SleepTimerActions(
    val onCountdown: (minutes: Int) -> Unit,
    val onExtend: (minutes: Int) -> Unit,
    val onEndOfItem: () -> Unit,
    val onCancel: () -> Unit
)

/**
 * Picks when playback stops. "End of episode" is the option podcast listeners actually use — a
 * fixed countdown cuts a conversation mid-sentence — so it is named for episodes when one plays.
 */
@Composable
internal fun SleepTimerSheet(
    state: SleepTimerState,
    isEpisode: Boolean,
    actions: SleepTimerActions,
    onDismiss: () -> Unit
) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    WandaSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            if (state is SleepTimerState.Countdown) {
                Remaining(state)
                TimerOption(
                    Icons.Rounded.MoreTime,
                    pluralStringResource(R.plurals.sleep_timer_extend, EXTEND_MINUTES, EXTEND_MINUTES)
                ) { actions.onExtend(EXTEND_MINUTES) }
            }

            Durations.forEach { minutes ->
                TimerOption(Icons.Rounded.Timer, pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes)) {
                    actions.onCountdown(minutes)
                    onDismiss()
                }
            }
            TimerOption(Icons.Rounded.Tune, stringResource(R.string.sleep_timer_custom), selected = showCustom) {
                showCustom = !showCustom
            }
            if (showCustom) {
                CustomDuration { minutes ->
                    actions.onCountdown(minutes)
                    onDismiss()
                }
            }
            TimerOption(
                icon = Icons.Rounded.VerticalAlignBottom,
                label = stringResource(if (isEpisode) R.string.sleep_timer_end_of_episode else R.string.sleep_timer_end_of_track),
                selected = state == SleepTimerState.EndOfItem
            ) {
                actions.onEndOfItem()
                onDismiss()
            }
            if (state != SleepTimerState.Off) {
                TimerOption(Icons.Rounded.BedtimeOff, stringResource(R.string.sleep_timer_off)) {
                    actions.onCancel()
                    onDismiss()
                }
            }
        }
    }
}

/** A slider over [CUSTOM_MIN]..[CUSTOM_MAX] minutes in [CUSTOM_STEP]s, and the button that starts it. */
@Composable
private fun CustomDuration(onStart: (minutes: Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(CUSTOM_DEFAULT) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text = pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes),
            style = MaterialTheme.typography.titleMedium
        )
        Slider(
            value = minutes.toFloat(),
            onValueChange = { minutes = (it / CUSTOM_STEP).roundToInt() * CUSTOM_STEP },
            valueRange = CUSTOM_MIN.toFloat()..CUSTOM_MAX.toFloat(),
            steps = (CUSTOM_MAX - CUSTOM_MIN) / CUSTOM_STEP - 1
        )
        Button(onClick = { onStart(minutes) }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.sleep_timer_start))
        }
    }
}

@Composable
private fun Remaining(state: SleepTimerState.Countdown) {
    val left = rememberTimeLeft(state)
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
