package com.wander.android.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * A miniature of the real speed/pitch dialog (`SpeedPitchPopup.kt`): an icon chip, its title, and
 * two simplified slider tracks — no preset chips, no interaction, just enough to read as *that*
 * dialog. Scales and fades in as the hold ramps up ([progress]), matching the fingertip's own
 * press ring in [Cursor].
 */
@Composable
internal fun SpeedPitchOverlay(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    val scheme = MaterialTheme.colorScheme

    Surface(
        color = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth(0.78f)
            .graphicsLayer {
                val scale = PopupMinScale + (1f - PopupMinScale) * progress
                scaleX = scale
                scaleY = scale
                alpha = progress
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.action_speed_and_pitch),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
            SliderTrack(scheme, filled = 0.62f)
            SliderTrack(scheme, filled = 0.5f)
        }
    }
}

@Composable
private fun SliderTrack(scheme: ColorScheme, filled: Float) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrackHeight)
                .clip(CircleShape)
                .background(scheme.onSurface.copy(alpha = 0.16f))
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(filled)
                    .height(TrackHeight)
                    .clip(CircleShape)
                    .background(scheme.primary.copy(alpha = 0.7f))
            )
            Box(
                modifier = Modifier
                    .size(ThumbSize)
                    .clip(CircleShape)
                    .background(scheme.primary)
            )
            Spacer(modifier = Modifier.weight(1f - filled))
        }
    }
}

private const val PopupMinScale = 0.85f
private val TrackHeight = 4.dp
private val ThumbSize = 10.dp
