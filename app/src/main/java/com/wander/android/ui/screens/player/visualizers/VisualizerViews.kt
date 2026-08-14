package com.wander.android.ui.screens.player.visualizers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.wander.android.core.audio.visualizer.VisualizerMode
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun WanderVisualizerHost(
    mode: VisualizerMode,
    spectrum: FloatArray,
    waveform: FloatArray,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    when (mode) {
        VisualizerMode.AURORA -> AuroraRibbonView(spectrum, primaryColor, secondaryColor, modifier)
        VisualizerMode.EMBERS -> EmbersView(spectrum, primaryColor, tertiaryColor, modifier)
        VisualizerMode.BLOOM -> BloomRingsView(spectrum, primaryColor, modifier)
        VisualizerMode.OSCILLOSCOPE -> OscilloscopeView(waveform, primaryColor, modifier)
        VisualizerMode.WATERFALL -> SpectrogramView(spectrum, primaryColor, secondaryColor, modifier)
        VisualizerMode.OFF -> {}
    }
}

@Composable
fun AuroraRibbonView(
    spectrum: FloatArray,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    val ribbonPath = remember { Path() }
    val glowPath = remember { Path() }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                phase += 0.035f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val pointCount = spectrum.size.coerceAtLeast(2)
        val stepX = width / (pointCount - 1)

        ribbonPath.reset()
        glowPath.reset()

        for (i in 0 until pointCount) {
            val band = if (i < spectrum.size) spectrum[i] else 0f
            val drift = sin((i * 0.2f) + phase) * 0.15f
            val normalizedLevel = (band * 0.75f + 0.15f + drift).coerceIn(0f, 1f)
            val y = height * (1.0f - normalizedLevel)
            val x = i * stepX

            if (i == 0) {
                ribbonPath.moveTo(x, y)
                glowPath.moveTo(x, y + 15f)
            } else {
                ribbonPath.lineTo(x, y)
                glowPath.lineTo(x, y + 15f)
            }
        }

        // Draw Ambient Ribbon Glow
        drawPath(
            path = glowPath,
            brush = Brush.verticalGradient(
                colors = listOf(secondaryColor.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = height
            ),
            style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw Sharp Aurora Ribbon
        drawPath(
            path = ribbonPath,
            brush = Brush.horizontalGradient(
                colors = listOf(primaryColor, secondaryColor)
            ),
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun EmbersView(
    spectrum: FloatArray,
    primaryColor: Color,
    flameColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cols = spectrum.size.coerceAtLeast(1)
        val barWidth = width / cols

        for (i in 0 until cols) {
            val level = if (i < spectrum.size) spectrum[i] else 0f
            val flameHeight = (level * height * 0.85f).coerceAtLeast(4f)
            val x = i * barWidth

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(flameColor, primaryColor.copy(alpha = 0.8f), Color.Transparent),
                    startY = height - flameHeight,
                    endY = height
                ),
                topLeft = Offset(x + barWidth * 0.15f, height - flameHeight),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.7f, flameHeight)
            )
        }
    }
}

@Composable
fun BloomRingsView(
    spectrum: FloatArray,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    // Fast bass energy computation (first 4 bands) without allocations
    val bassEnergy = remember(spectrum) {
        if (spectrum.size >= 4) {
            (spectrum[0] + spectrum[1] + spectrum[2] + spectrum[3]) * 0.25f
        } else if (spectrum.isNotEmpty()) {
            spectrum[0]
        } else {
            0f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension / 2.2f
        val pulseRadius = maxRadius * (0.35f + bassEnergy * 0.65f)

        // Outer Shockwave
        drawCircle(
            color = primaryColor.copy(alpha = (bassEnergy * 0.4f).coerceIn(0f, 0.6f)),
            radius = pulseRadius * 1.3f,
            center = center,
            style = Stroke(width = 6f)
        )

        // Inner Core Ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor.copy(alpha = 0.5f), Color.Transparent),
                center = center,
                radius = pulseRadius
            ),
            radius = pulseRadius,
            center = center
        )
    }
}

@Composable
fun OscilloscopeView(
    waveform: FloatArray,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val oscPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val pointCount = waveform.size.coerceAtLeast(2)
        val stepX = width / (pointCount - 1)

        oscPath.reset()
        for (i in 0 until pointCount) {
            val sample = if (i < waveform.size) waveform[i] else 0f
            val x = i * stepX
            val y = centerY + (sample * height * 0.4f)
            if (i == 0) oscPath.moveTo(x, y) else oscPath.lineTo(x, y)
        }

        drawPath(
            path = oscPath,
            color = primaryColor,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun SpectrogramView(
    spectrum: FloatArray,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val cols = spectrum.size.coerceAtLeast(1)
        val barWidth = width / cols

        for (i in 0 until cols) {
            val level = if (i < spectrum.size) spectrum[i] else 0f
            val barHeight = level * height
            val x = i * barWidth

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor, secondaryColor),
                    startY = height - barHeight,
                    endY = height
                ),
                topLeft = Offset(x + 2f, height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 4f, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
}
