package com.wander.android.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wander.android.R
import com.wander.android.core.playback.SpeedAndPitch

/**
 * Material 3 Expressive dialog for adjusting playback speed and pitch.
 * Centered on screen rather than spawning at the touch point.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpeedPitchHeader()

                RateSection(
                    label = stringResource(R.string.speed_label),
                    icon = Icons.Rounded.Speed,
                    rate = value.speed,
                    presets = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
                    onRate = { onChange(value.copy(speed = it)) }
                )

                RateSection(
                    label = stringResource(R.string.pitch_label),
                    icon = Icons.Rounded.GraphicEq,
                    rate = value.pitch,
                    presets = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.2f),
                    onRate = { onChange(value.copy(pitch = it)) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onChange(SpeedAndPitch()) },
                        enabled = !value.isDefault,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Text(
                            text = stringResource(R.string.action_reset),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

/** A cookie-cut emblem beside the title, the way expressive dialogs open. */
@Composable
private fun SpeedPitchHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Column {
            Text(
                text = stringResource(R.string.action_speed_and_pitch),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.speed_pitch_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
