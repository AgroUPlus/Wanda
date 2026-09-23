package com.wander.android.ui.screens.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val TopFadeHeight = 56.dp
private val BottomFadeHeight = 220.dp

/**
 * Fades the content out at the top and bottom edges instead of cutting it.
 *
 * Drawn as a mask rather than a gradient laid over the top: an overlay would have to be painted in
 * the background's own colour to hide anything, and there is no one such colour here — the
 * background is whatever the cover tinted it to this track. Masking the content's own alpha works
 * against any background because it never paints anything.
 *
 * `CompositingStrategy.Offscreen` is what makes that possible: `BlendMode.DstIn` needs a layer to
 * blend against, and without it the blend has nothing to erase from.
 */
internal fun Modifier.fadeVerticalEdges(
    top: () -> Float,
    bottom: () -> Float
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val topFactor = top()
        val bottomFactor = bottom()
        val topHeight = TopFadeHeight.toPx().coerceAtMost(size.height / 3f) * topFactor
        // Deeper at the foot, and measured from *below* the bar: the lines should still be
        // legible as they pass behind the transport and be gone by the time they reach the
        // screen's edge, rather than dissolving before they get there.
        val bottomHeight = BottomFadeHeight.toPx().coerceAtMost(size.height / 2f) * bottomFactor
        if (topHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = topHeight
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomHeight > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - bottomHeight,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
