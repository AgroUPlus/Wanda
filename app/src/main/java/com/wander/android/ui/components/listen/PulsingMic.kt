package com.wander.android.ui.components.listen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An organic, fluid, audio-reactive wave animation for the microphone in Android 17 / M3 Expressive style.
 *
 * Replaces rigid concentric rings with layered liquid spline waves that gently undulate while idle
 * and dynamically swell, ripple, and pulse in real time with the live sound amplitude.
 */
@Composable
internal fun PulsingMic(
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val smoothedLevel by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "micAudioLevel"
    )

    val transition = rememberInfiniteTransition(label = "organicWave")

    // Slow organic breathing rotation
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3_800, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase1"
    )

    // Counter-rotating harmonic wave
    val phase2 by transition.animateFloat(
        initialValue = (2f * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(4_600, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase2"
    )

    // Idle breathing pulse (so it feels alive even in dead silence)
    val idlePulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "idlePulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(CanvasSize)) {
        Canvas(modifier = Modifier.size(CanvasSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f

            // Effective sound energy: combination of idle life and live audio level
            val energy = (smoothedLevel * 0.85f + 0.15f * idlePulse).coerceIn(0.1f, 1.2f)

            // 1. Layer 1: Outermost ethereal aura (flowing tertiary -> primary gradient)
            val auraRadius = baseRadius * (0.65f + 0.30f * energy)
            drawOrganicBlob(
                center = center,
                radius = auraRadius,
                waveform = BlobWaveform(
                    phase = phase1,
                    distortion = 0.14f + 0.12f * smoothedLevel,
                    lobes = 4,
                    harmonicLobes = 7,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tertiaryColor.copy(alpha = 0.22f + 0.18f * smoothedLevel),
                            primaryColor.copy(alpha = 0.08f + 0.12f * smoothedLevel),
                            Color.Transparent
                        ),
                        center = center,
                        radius = auraRadius * 1.1f
                    ),
                    strokeColor = tertiaryColor.copy(alpha = 0.25f + 0.35f * smoothedLevel),
                    strokeWidth = (2f + 2f * smoothedLevel).dp.toPx()
                )
            )

            // 2. Layer 2: Middle liquid wave (counter-rotating, energetic, vibrant)
            val waveRadius = baseRadius * (0.50f + 0.32f * energy)
            drawOrganicBlob(
                center = center,
                radius = waveRadius,
                waveform = BlobWaveform(
                    phase = phase2,
                    distortion = 0.18f + 0.16f * smoothedLevel,
                    lobes = 5,
                    harmonicLobes = 3,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.38f + 0.25f * smoothedLevel),
                            secondaryColor.copy(alpha = 0.18f + 0.15f * smoothedLevel),
                            primaryColor.copy(alpha = 0.02f)
                        ),
                        center = center,
                        radius = waveRadius * 1.05f
                    ),
                    strokeColor = primaryColor.copy(alpha = 0.45f + 0.40f * smoothedLevel),
                    strokeWidth = (2.5f + 2f * smoothedLevel).dp.toPx()
                )
            )

            // 3. Layer 3: Inner soft core glow
            val coreRadius = baseRadius * (0.34f + 0.10f * energy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f + 0.20f * smoothedLevel),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.2f
                ),
                radius = coreRadius,
                center = center
            )
        }

        // Central floating Material 3 Expressive pill/circle with mic icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .scale(1f + smoothedLevel * 0.10f)
                .shadow(elevation = (6 + (smoothedLevel * 10).toInt()).dp, shape = CircleShape)
                .clip(CircleShape)
                .background(containerColor)
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

private data class BlobWaveform(
    val phase: Float,
    val distortion: Float,
    val lobes: Int,
    val harmonicLobes: Int,
    val brush: Brush,
    val strokeColor: Color,
    val strokeWidth: Float
)

/**
 * Draws a continuous organic fluid blob using a closed cubic/quadratic Bézier spline around [center].
 */
private fun DrawScope.drawOrganicBlob(
    center: Offset,
    radius: Float,
    waveform: BlobWaveform
) {
    if (radius <= 0f) return

    val pointCount = 12
    val points = ArrayList<Offset>(pointCount)

    for (i in 0 until pointCount) {
        val theta = (i.toFloat() / pointCount) * 2f * PI.toFloat()
        // Combine primary lobes and secondary harmonic lobes with phase offsets
        val wave1 = sin(waveform.lobes * theta + waveform.phase)
        val wave2 = cos(waveform.harmonicLobes * theta - waveform.phase * 1.3f)
        val r = radius * (1f + waveform.distortion * (0.65f * wave1 + 0.35f * wave2))

        val x = center.x + r * cos(theta)
        val y = center.y + r * sin(theta)
        points.add(Offset(x, y))
    }

    // Build smooth closed spline using quadratic midpoints (ensures C1 smooth curvature everywhere)
    val path = Path().apply {
        val first = points[0]
        val last = points[pointCount - 1]
        val startMid = Offset((first.x + last.x) / 2f, (first.y + last.y) / 2f)

        moveTo(startMid.x, startMid.y)
        for (i in 0 until pointCount) {
            val curr = points[i]
            val next = points[(i + 1) % pointCount]
            val mid = Offset((curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
            quadraticTo(curr.x, curr.y, mid.x, mid.y)
        }
        close()
    }

    // Draw soft gradient fill
    drawPath(path = path, brush = waveform.brush, style = Fill)
    // Draw glowing undulating contour stroke
    drawPath(path = path, color = waveform.strokeColor, style = Stroke(width = waveform.strokeWidth))
}

private val CanvasSize = 170.dp

