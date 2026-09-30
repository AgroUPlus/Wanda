package com.wander.android.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

internal fun DrawScope.drawEyes(
    style: Int,
    color: Color,
    leftX: Float,
    rightX: Float,
    y: Float,
    size: Float
) {
    val strokeWidth = size * 0.055f
    when (style % 5) {
        0 -> {
            val eyeRadius = size * 0.07f
            drawCircle(color = color, radius = eyeRadius, center = Offset(leftX, y))
            drawCircle(color = color, radius = eyeRadius, center = Offset(rightX, y))
            val glintRadius = size * 0.024f
            drawCircle(color = Color.White, radius = glintRadius, center = Offset(leftX + eyeRadius * 0.3f, y - eyeRadius * 0.3f))
            drawCircle(color = Color.White, radius = glintRadius, center = Offset(rightX + eyeRadius * 0.3f, y - eyeRadius * 0.3f))
            val miniGlint = size * 0.012f
            drawCircle(color = Color.White, radius = miniGlint, center = Offset(leftX - eyeRadius * 0.25f, y + eyeRadius * 0.3f))
            drawCircle(color = Color.White, radius = miniGlint, center = Offset(rightX - eyeRadius * 0.25f, y + eyeRadius * 0.3f))
        }
        1 -> {
            val r = size * 0.075f
            val pathLeft = Path().apply {
                moveTo(leftX - r, y + r * 0.25f)
                quadraticTo(leftX, y - r * 0.9f, leftX + r, y + r * 0.25f)
            }
            val pathRight = Path().apply {
                moveTo(rightX - r, y + r * 0.25f)
                quadraticTo(rightX, y - r * 0.9f, rightX + r, y + r * 0.25f)
            }
            drawPath(pathLeft, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawPath(pathRight, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        2 -> {
            val r = size * 0.07f
            val winkPath = Path().apply {
                moveTo(leftX - r, y - r * 0.3f)
                lineTo(leftX, y)
                lineTo(leftX - r, y + r * 0.3f)
            }
            drawPath(winkPath, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawCircle(color = color, radius = r, center = Offset(rightX, y))
            drawCircle(color = Color.White, radius = r * 0.35f, center = Offset(rightX + r * 0.3f, y - r * 0.3f))
            drawCircle(color = Color.White, radius = r * 0.18f, center = Offset(rightX - r * 0.2f, y + r * 0.3f))
        }
        3 -> {
            fun drawStarEye(cx: Float, cy: Float, radius: Float) {
                val p = Path().apply {
                    moveTo(cx, cy - radius)
                    quadraticTo(cx, cy, cx + radius, cy)
                    quadraticTo(cx, cy, cx, cy + radius)
                    quadraticTo(cx, cy, cx - radius, cy)
                    quadraticTo(cx, cy, cx, cy - radius)
                }
                drawPath(p, color = color)
                drawCircle(Color.White, radius * 0.28f, Offset(cx + radius * 0.25f, cy - radius * 0.25f))
            }
            drawStarEye(leftX, y, size * 0.08f)
            drawStarEye(rightX, y, size * 0.08f)
        }
        else -> {
            val ovalW = size * 0.09f
            val ovalH = size * 0.12f
            drawRoundRect(
                color = color,
                topLeft = Offset(leftX - ovalW / 2, y - ovalH / 2),
                size = Size(ovalW, ovalH),
                cornerRadius = CornerRadius(ovalW / 2, ovalW / 2)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(rightX - ovalW / 2, y - ovalH / 2),
                size = Size(ovalW, ovalH),
                cornerRadius = CornerRadius(ovalW / 2, ovalW / 2)
            )
            drawCircle(color = Color.White, radius = size * 0.025f, center = Offset(leftX + ovalW * 0.15f, y - ovalH * 0.2f))
            drawCircle(color = Color.White, radius = size * 0.025f, center = Offset(rightX + ovalW * 0.15f, y - ovalH * 0.2f))
            drawCircle(color = Color.White, radius = size * 0.012f, center = Offset(leftX - ovalW * 0.15f, y + ovalH * 0.2f))
            drawCircle(color = Color.White, radius = size * 0.012f, center = Offset(rightX - ovalW * 0.15f, y + ovalH * 0.2f))
        }
    }
}

internal fun DrawScope.drawMouth(
    style: Int,
    color: Color,
    centerX: Float,
    y: Float,
    size: Float
) {
    val strokeWidth = size * 0.05f
    when (style % 4) {
        0 -> {
            val w = size * 0.11f
            val h = size * 0.07f
            val path = Path().apply {
                moveTo(centerX - w, y)
                quadraticTo(centerX, y + h, centerX + w, y)
            }
            drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        1 -> {
            val w = size * 0.12f
            val h = size * 0.09f
            val mouthPath = Path().apply {
                moveTo(centerX - w, y)
                quadraticTo(centerX, y + h * 1.6f, centerX + w, y)
                close()
            }
            drawPath(mouthPath, color = color)
            val tonguePath = Path().apply {
                moveTo(centerX - w * 0.6f, y + h * 0.5f)
                quadraticTo(centerX, y + h * 1.4f, centerX + w * 0.6f, y + h * 0.5f)
                close()
            }
            drawPath(tonguePath, color = Color(0xFFFF758F))
        }
        2 -> {
            val w = size * 0.065f
            val h = size * 0.05f
            val pathLeft = Path().apply {
                moveTo(centerX - w * 2, y)
                quadraticTo(centerX - w, y + h, centerX, y)
            }
            val pathRight = Path().apply {
                moveTo(centerX, y)
                quadraticTo(centerX + w, y + h, centerX + w * 2, y)
            }
            drawPath(pathLeft, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawPath(pathRight, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        else -> {
            val w = size * 0.13f
            val h = size * 0.08f
            val path = Path().apply {
                moveTo(centerX - w, y - h * 0.2f)
                quadraticTo(centerX - w * 0.5f, y + h, centerX, y + h)
                quadraticTo(centerX + w * 0.5f, y + h, centerX + w, y - h * 0.2f)
            }
            drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
    }
}

internal fun DrawScope.drawAccessory(
    style: Int,
    color: Color,
    width: Float,
    height: Float
) {
    when (style % 5) {
        0 -> {
            val earRadius = width * 0.13f
            drawCircle(color = color.copy(alpha = 0.55f), radius = earRadius, center = Offset(width * 0.22f, height * 0.20f))
            drawCircle(color = color.copy(alpha = 0.55f), radius = earRadius, center = Offset(width * 0.78f, height * 0.20f))
            drawCircle(color = Color.White.copy(alpha = 0.4f), radius = earRadius * 0.55f, center = Offset(width * 0.22f, height * 0.20f))
            drawCircle(color = Color.White.copy(alpha = 0.4f), radius = earRadius * 0.55f, center = Offset(width * 0.78f, height * 0.20f))
        }
        1 -> {
            val stemPath = Path().apply {
                moveTo(width * 0.5f, height * 0.24f)
                quadraticTo(width * 0.48f, height * 0.15f, width * 0.5f, height * 0.08f)
            }
            drawPath(stemPath, color = color, style = Stroke(width = width * 0.04f, cap = StrokeCap.Round))
            val leafPath = Path().apply {
                moveTo(width * 0.5f, height * 0.09f)
                quadraticTo(width * 0.66f, height * 0.04f, width * 0.63f, height * 0.14f)
                quadraticTo(width * 0.54f, height * 0.14f, width * 0.5f, height * 0.09f)
            }
            drawPath(leafPath, color = color)
        }
        2 -> {
            val sx = width * 0.78f
            val sy = height * 0.24f
            val sRadius = width * 0.065f
            val sparkPath = Path().apply {
                moveTo(sx, sy - sRadius)
                quadraticTo(sx, sy, sx + sRadius, sy)
                quadraticTo(sx, sy, sx, sy + sRadius)
                quadraticTo(sx, sy, sx - sRadius, sy)
                quadraticTo(sx, sy, sx, sy - sRadius)
            }
            drawPath(sparkPath, color = color.copy(alpha = 0.85f))
        }
        3 -> {
            val haloRect = Path().apply {
                moveTo(width * 0.35f, height * 0.14f)
                quadraticTo(width * 0.5f, height * 0.08f, width * 0.65f, height * 0.14f)
                quadraticTo(width * 0.5f, height * 0.20f, width * 0.35f, height * 0.14f)
            }
            drawPath(haloRect, color = color.copy(alpha = 0.7f), style = Stroke(width = width * 0.035f, cap = StrokeCap.Round))
        }
        else -> {
            val sRadius = width * 0.045f
            fun drawMiniStar(sx: Float, sy: Float) {
                val p = Path().apply {
                    moveTo(sx, sy - sRadius)
                    quadraticTo(sx, sy, sx + sRadius, sy)
                    quadraticTo(sx, sy, sx, sy + sRadius)
                    quadraticTo(sx, sy, sx - sRadius, sy)
                    quadraticTo(sx, sy, sx, sy - sRadius)
                }
                drawPath(p, color = color.copy(alpha = 0.75f))
            }
            drawMiniStar(width * 0.22f, height * 0.22f)
            drawMiniStar(width * 0.78f, height * 0.22f)
        }
    }
}
