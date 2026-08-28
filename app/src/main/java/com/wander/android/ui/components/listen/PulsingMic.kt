package com.wander.android.ui.components.listen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A microphone under expanding rings, for as long as the app is listening.
 *
 * Rings rather than a spinner. A spinner means "working, no idea how long"; this has to say
 * "the microphone is open right now", which is a claim about the device the user is entitled to
 * see at a glance — and to see stop the instant it stops.
 *
 * Three rings on one clock, offset in phase, so the pulse reads as continuous rather than as a
 * shape that restarts.
 */
@Composable
internal fun PulsingMic(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_PERIOD_MS), RepeatMode.Restart),
        label = "ring"
    )

    val ringColor = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(MicSize)) {
        Canvas(modifier = Modifier.size(MicSize)) {
            val maxRadius = size.minDimension / 2f
            repeat(RING_COUNT) { index ->
                // Each ring is the same animation a third of a cycle apart. `% 1f` wraps the
                // offset so a ring that would be "ahead of the end" simply starts again.
                val progress = (phase + index.toFloat() / RING_COUNT) % 1f
                drawCircle(
                    color = ringColor,
                    radius = maxRadius * progress,
                    // Fades as it grows, so a ring never pops out of existence at full size.
                    alpha = (1f - progress) * RING_MAX_ALPHA,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
    }
}

private val MicSize = 140.dp
private const val RING_COUNT = 3
private const val RING_PERIOD_MS = 2_000
private const val RING_MAX_ALPHA = 0.55f
