package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.SpeedAndPitch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One rate (speed or pitch) as an expressive control group: a large rolling readout,
 * fine −/+ steps around a thick slider, and a connected row of presets.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RateSection(
    label: String,
    icon: ImageVector,
    rate: Float,
    presets: List<Float>,
    onRate: (Float) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                RollingRate(rate)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onRate(step(rate, -1)) },
                    enabled = rate > SpeedAndPitch.RANGE.start,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.speed_pitch_lower, label))
                }
                val interaction = remember { MutableInteractionSource() }
                Slider(
                    value = rate,
                    onValueChange = { onRate(snap(it)) },
                    valueRange = SpeedAndPitch.RANGE,
                    interactionSource = interaction,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interaction,
                            thumbSize = DpSize(4.dp, 40.dp)
                        )
                    },
                    track = { state ->
                        SliderDefaults.Track(
                            sliderState = state,
                            drawTick = { _, _ -> },
                            modifier = Modifier.height(TRACK_HEIGHT)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = { onRate(step(rate, 1)) },
                    enabled = rate < SpeedAndPitch.RANGE.endInclusive,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.speed_pitch_raise, label))
                }
            }

            // Labels are resolved here: ButtonGroup's item builders are not composable scopes.
            val labels = presets.map(::formatRate)
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                presets.forEachIndexed { i, preset ->
                    toggleableItem(
                        checked = abs(rate - preset) < HALF_STEP,
                        label = labels[i],
                        onCheckedChange = { onRate(preset) },
                        weight = 1f
                    )
                }
            }
        }
    }
}

/** The current rate in a pill, rolling up or down as it changes. */
@Composable
private fun RollingRate(rate: Float) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        AnimatedContent(
            targetState = rate,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(spatial) { if (up) it else -it } + fadeIn(effects))
                    .togetherWith(slideOutVertically(spatial) { if (up) -it else it } + fadeOut(effects))
            },
            label = "rate"
        ) { shown ->
            Text(
                text = formatRate(shown),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
    }
}

private fun formatRate(rate: Float) = String.format(Locale.US, "%.2f×", rate)

/** Rounds to the 0.05 grid the slider used to tick on, so presets and steps line up. */
private fun snap(rate: Float): Float =
    ((rate / STEP).roundToInt() * STEP)
        .coerceIn(SpeedAndPitch.RANGE.start, SpeedAndPitch.RANGE.endInclusive)

private fun step(rate: Float, direction: Int) = snap(rate + direction * STEP)

private const val STEP = 0.05f
private const val HALF_STEP = STEP / 2
private val TRACK_HEIGHT = 16.dp
