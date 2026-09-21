package com.wander.android.ui.screens.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wander.android.R
import com.wander.android.core.playback.SpeedAndPitch
import com.wander.android.ui.components.rememberPressScale
import java.util.Locale

/**
 * Material 3 Expressive dialog for adjusting playback speed and pitch.
 * Centered on screen rather than spawning at the touch point.
 */
@Composable
internal fun SpeedPitchPopup(
    value: SpeedAndPitch,
    onChange: (SpeedAndPitch) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expressive Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.action_speed_and_pitch),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.speed_pitch_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Speed Section
                ExpressiveRateSlider(
                    label = stringResource(R.string.speed_label),
                    rate = value.speed,
                    presets = listOf(0.75f, 1.0f, 1.25f, 1.5f),
                    onRate = { onChange(value.copy(speed = it)) }
                )

                // Pitch Section
                ExpressiveRateSlider(
                    label = stringResource(R.string.pitch_label),
                    rate = value.pitch,
                    presets = listOf(0.9f, 1.0f, 1.1f),
                    onRate = { onChange(value.copy(pitch = it)) }
                )

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onChange(SpeedAndPitch()) },
                        enabled = !value.isDefault,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.action_reset))
                    }
                    FilledTonalButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveRateSlider(
    label: String,
    rate: Float,
    presets: List<Float>,
    onRate: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.2f×", rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Slider(
            value = rate,
            onValueChange = onRate,
            valueRange = SpeedAndPitch.RANGE,
            steps = STEPS
        )

        // Preset Chips Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                val isSelected = kotlin.math.abs(rate - preset) < 0.02f
                val interaction = remember { MutableInteractionSource() }
                val scale by rememberPressScale(interaction)
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onRate(preset) },
                    interactionSource = interaction,
                    modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f×", preset),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** Interior stops only, which is what `Slider` counts. */
private const val STEPS = 29
