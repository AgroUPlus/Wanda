package com.wander.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kinetic dancing equalizer bars for tracks that are currently playing.
 *
 * Replaces static equalizer icons with a breathing, lively 3-bar kinetic wave. When playback
 * pauses, the bars smoothly settle to resting dots rather than freezing in place.
 */
@Composable
fun KineticEqualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    maxHeight: Dp = 16.dp,
    barWidth: Dp = 3.dp,
    minHeight: Dp = 3.dp
) {
    val transition = rememberInfiniteTransition(label = "kineticEqualizer")

    val bar1Phase by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 440, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar1"
    )

    val bar2Phase by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 330, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar2"
    )

    val bar3Phase by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar3"
    )

    val heightSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val dynamicBar1 by animateDpAsState(
        targetValue = if (isPlaying) minHeight + (maxHeight - minHeight) * bar1Phase else minHeight,
        animationSpec = heightSpec,
        label = "bar1Height"
    )
    val dynamicBar2 by animateDpAsState(
        targetValue = if (isPlaying) minHeight + (maxHeight - minHeight) * bar2Phase else minHeight,
        animationSpec = heightSpec,
        label = "bar2Height"
    )
    val dynamicBar3 by animateDpAsState(
        targetValue = if (isPlaying) minHeight + (maxHeight - minHeight) * bar3Phase else minHeight,
        animationSpec = heightSpec,
        label = "bar3Height"
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .height(maxHeight)
            .width(barWidth * 3 + 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(dynamicBar1)
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(dynamicBar2)
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(dynamicBar3)
                .background(color, CircleShape)
        )
    }
}
