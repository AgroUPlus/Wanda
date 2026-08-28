package com.wander.android.ui.screens.importer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.data.importer.PlatformType

@Composable
fun PlatformIcon(
    platform: PlatformType,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(size)) {
        when (platform) {
            PlatformType.SPOTIFY -> drawSpotifyLogo(tint)
            PlatformType.DEEZER -> drawDeezerLogo(tint)
            PlatformType.YOUTUBE -> drawYouTubeMusicLogo(tint)
            PlatformType.APPLE_MUSIC -> drawAppleMusicLogo(tint)
            PlatformType.PLAIN_TEXT -> drawTextLogo(tint)
        }
    }
}

private fun DrawScope.drawSpotifyLogo(tint: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f
    drawCircle(color = tint, radius = radius, style = Stroke(width = size.width * 0.08f))

    val strokeWidth = size.width * 0.09f
    // Top wave
    val path1 = Path().apply {
        moveTo(size.width * 0.28f, size.height * 0.40f)
        quadraticTo(size.width * 0.50f, size.height * 0.33f, size.width * 0.72f, size.height * 0.42f)
    }
    drawPath(path1, tint, style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round))

    // Middle wave
    val path2 = Path().apply {
        moveTo(size.width * 0.32f, size.height * 0.54f)
        quadraticTo(size.width * 0.50f, size.height * 0.48f, size.width * 0.68f, size.height * 0.56f)
    }
    drawPath(path2, tint, style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round))

    // Bottom wave
    val path3 = Path().apply {
        moveTo(size.width * 0.36f, size.height * 0.68f)
        quadraticTo(size.width * 0.50f, size.height * 0.64f, size.width * 0.64f, size.height * 0.70f)
    }
    drawPath(path3, tint, style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round))
}

private fun DrawScope.drawDeezerLogo(tint: Color) {
    // Deezer equalizer bars shape
    val barWidth = size.width * 0.12f
    val barSpacing = size.width * 0.06f
    val heights = floatArrayOf(0.45f, 0.75f, 0.90f, 0.60f, 0.35f)

    for (i in heights.indices) {
        val x = size.width * 0.10f + i * (barWidth + barSpacing)
        val h = size.height * heights[i]
        val y = size.height * 0.5f - h / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(x, y),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
    }
}

private fun DrawScope.drawYouTubeMusicLogo(tint: Color) {
    val radius = size.minDimension / 2f
    drawCircle(color = tint, radius = radius, style = Stroke(width = size.width * 0.08f))
    drawCircle(color = tint, radius = radius * 0.65f, style = Stroke(width = size.width * 0.06f))

    // Play triangle in center
    val triangle = Path().apply {
        moveTo(size.width * 0.44f, size.height * 0.36f)
        lineTo(size.width * 0.62f, size.height * 0.50f)
        lineTo(size.width * 0.44f, size.height * 0.64f)
        close()
    }
    drawPath(triangle, tint, style = Fill)
}

private fun DrawScope.drawAppleMusicLogo(tint: Color) {
    // Apple Music double eighth note
    val notePath = Path().apply {
        // Left note head
        addOval(androidx.compose.ui.geometry.Rect(size.width * 0.20f, size.height * 0.62f, size.width * 0.42f, size.height * 0.82f))
        // Right note head
        addOval(androidx.compose.ui.geometry.Rect(size.width * 0.58f, size.height * 0.50f, size.width * 0.80f, size.height * 0.70f))
    }
    drawPath(notePath, tint, style = Fill)

    // Left stem
    drawLine(tint, Offset(size.width * 0.39f, size.height * 0.70f), Offset(size.width * 0.39f, size.height * 0.28f), strokeWidth = size.width * 0.09f, cap = StrokeCap.Round)
    // Right stem
    drawLine(tint, Offset(size.width * 0.77f, size.height * 0.58f), Offset(size.width * 0.77f, size.height * 0.18f), strokeWidth = size.width * 0.09f, cap = StrokeCap.Round)
    // Connecting beam
    val beam = Path().apply {
        moveTo(size.width * 0.35f, size.height * 0.28f)
        lineTo(size.width * 0.81f, size.height * 0.16f)
        lineTo(size.width * 0.81f, size.height * 0.26f)
        lineTo(size.width * 0.35f, size.height * 0.38f)
        close()
    }
    drawPath(beam, tint, style = Fill)
}

private fun DrawScope.drawTextLogo(tint: Color) {
    val strokeWidth = size.width * 0.08f
    drawRoundRect(
        color = tint,
        topLeft = Offset(size.width * 0.2f, size.height * 0.15f),
        size = Size(size.width * 0.6f, size.height * 0.7f),
        cornerRadius = CornerRadius(size.width * 0.08f, size.width * 0.08f),
        style = Stroke(width = strokeWidth)
    )
    drawLine(tint, Offset(size.width * 0.32f, size.height * 0.35f), Offset(size.width * 0.68f, size.height * 0.35f), strokeWidth = strokeWidth * 0.8f, cap = StrokeCap.Round)
    drawLine(tint, Offset(size.width * 0.32f, size.height * 0.50f), Offset(size.width * 0.68f, size.height * 0.50f), strokeWidth = strokeWidth * 0.8f, cap = StrokeCap.Round)
    drawLine(tint, Offset(size.width * 0.32f, size.height * 0.65f), Offset(size.width * 0.52f, size.height * 0.65f), strokeWidth = strokeWidth * 0.8f, cap = StrokeCap.Round)
}
