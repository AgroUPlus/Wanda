package com.wander.android.ui.components.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How many soft bumps the ring and the button's own outline share while playing — see
 * [rememberCookieMorphShape]. Low and shallow on purpose: this sits at icon size, and a tight,
 * deep scallop at that scale reads as a jagged gear rather than a soft, rounded cookie.
 */
private const val CookieLobes = 6
private const val LobeDepthFraction = 0.05f

/** The two colours of a [CircularProgressRing]: the full-circle track and the progress sweep over it. */
internal class RingColors(val track: Color, val progress: Color)

@Composable
internal fun ringColors(
    track: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    progress: Color = MaterialTheme.colorScheme.primary
) = RingColors(track, progress)

/**
 * A smooth circular progress ring around a player control, tracking playback position — scalloping
 * into the same soft "cookie" edge as [rememberCookieMorphShape] while [amplitude] is above zero, so
 * the ring and the button it surrounds read as one shape rather than a circle boxing an unrelated
 * scalloped button.
 *
 * Both the ring and its [content] share one centre: [content] is placed by this [Box]'s own
 * [Alignment.Center], not by whatever alignment happened to be around the call site, so the button
 * cannot drift off the ring's middle regardless of where this composable is used from.
 */
@Composable
internal fun CircularProgressRing(
    progress: () -> Float,
    ringSize: Dp,
    modifier: Modifier = Modifier,
    amplitude: () -> Float = { 0f },
    strokeWidth: Dp = 3.dp,
    colors: RingColors = ringColors(),
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val amp = amplitude().coerceIn(0f, 1f)
            val strokeInset = strokeWidth.toPx() / 2f
            val maxRadius = min(size.width, size.height) / 2f - strokeInset
            val amplitudePx = maxRadius * LobeDepthFraction * amp
            val baseRadius = maxRadius - amplitudePx
            val style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

            drawPath(cookiePath(size, baseRadius, amplitudePx, sweepFraction = 1f), colors.track, style = style)

            val sweep = progress().coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawPath(cookiePath(size, baseRadius, amplitudePx, sweepFraction = sweep), colors.progress, style = style)
            }
        }
        content()
    }
}

/**
 * The button's own outline, morphing from a plain circle to the same soft cookie the ring around it
 * draws — see [CircularProgressRing]. [amplitude] is a lambda, not `isPlaying` itself, so a caller
 * driving both this shape and the ring shares the exact one animation between them rather than each
 * starting its own, merely identically-specced one.
 */
@Composable
internal fun rememberCookieMorphShape(amplitude: () -> Float): Shape =
    remember { CookieMorphShape(amplitude) }

private class CookieMorphShape(private val amplitude: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val amp = amplitude().coerceIn(0f, 1f)
        val maxRadius = min(size.width, size.height) / 2f
        if (amp <= 0.001f) {
            return Outline.Generic(Path().apply { addOval(Rect(center = size.center(), radius = maxRadius)) })
        }
        val amplitudePx = maxRadius * LobeDepthFraction * amp
        val baseRadius = maxRadius - amplitudePx
        return Outline.Generic(cookiePath(size, baseRadius, amplitudePx, sweepFraction = 1f))
    }
}

private fun Size.center(): Offset = Offset(width / 2f, height / 2f)

/**
 * A closed cookie outline (`sweepFraction = 1`) or an open arc of it starting at the top and
 * sweeping clockwise (`sweepFraction < 1`) — shared by the button's own fill and the ring's stroke,
 * always centred on [size]'s own centre, so the two never drift apart regardless of which one is
 * drawn at which radius.
 */
private fun cookiePath(size: Size, baseRadius: Float, amplitudePx: Float, sweepFraction: Float): Path {
    val center = size.center()
    val closed = sweepFraction >= 1f
    val totalAngle = 2.0 * PI * sweepFraction
    val pointCount = (CookieLobes * 16 * sweepFraction).roundToInt().coerceAtLeast(8)
    val path = Path()
    for (i in 0..pointCount) {
        val t = i / pointCount.toFloat()
        val theta = -PI / 2.0 + t * totalAngle
        val r = baseRadius + amplitudePx * cos(CookieLobes * theta)
        val x = center.x + (r * cos(theta)).toFloat()
        val y = center.y + (r * sin(theta)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (closed) path.close()
    return path
}
