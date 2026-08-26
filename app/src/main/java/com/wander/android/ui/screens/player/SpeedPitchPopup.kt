package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.wander.android.core.playback.SpeedAndPitch
import java.util.Locale

/**
 * Speed and pitch, opened by long-pressing the cover.
 *
 * A popup anchored at the touch point rather than a bottom sheet: this is an adjustment made
 * while listening, and it should not cover the thing being adjusted or take the player off
 * screen to reach it.
 *
 * The two are separate sliders because they are separate wishes. Slowing a track down usually
 * means keeping its key, and pitching it usually means keeping its tempo — tying them together
 * would remove the only reason to expose either.
 */
@Composable
internal fun SpeedPitchPopup(
    value: SpeedAndPitch,
    onChange: (SpeedAndPitch) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset
) {
    Popup(offset = offset, onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                RateSlider(
                    label = "Speed",
                    rate = value.speed,
                    onRate = { onChange(value.copy(speed = it)) }
                )
                RateSlider(
                    label = "Pitch",
                    rate = value.pitch,
                    onRate = { onChange(value.copy(pitch = it)) }
                )
                TextButton(
                    onClick = { onChange(SpeedAndPitch()) },
                    enabled = !value.isDefault,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
private fun RateSlider(label: String, rate: Float, onRate: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = String.format(Locale.US, "%.2f×", rate),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Slider(
        value = rate,
        onValueChange = onRate,
        valueRange = MIN_RATE..MAX_RATE,
        // 0.05× apart: fine enough to find 0.9 for a slow listen, coarse enough that a thumb
        // lands on a round number rather than 1.03.
        steps = STEPS
    )
}

private const val MIN_RATE = 0.5f
private const val MAX_RATE = 2.0f

/** Interior stops only, which is what `Slider` counts. */
private const val STEPS = 29
