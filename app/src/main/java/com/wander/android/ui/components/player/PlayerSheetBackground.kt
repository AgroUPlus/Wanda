package com.wander.android.ui.components.player

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as lerpColor

/** How dark the cover-derived background gets at full expansion. 0 = the seed itself, 1 = black. */
private const val DeepenAmount = 0.55f

/** How much of the deepened seed shows through at full expansion, blended over [expandedColor]. */
private const val SeedBlendAmount = 0.5f

/**
 * Draws the player sheet's background: a flat lerp between [dockedColor] and [expandedColor] when
 * [coverSeed] is null (cover-art theming off), or — when it isn't — a vertical gradient that
 * blends in a darkened version of the cover's seed colour as [progress] moves toward fully
 * expanded, so the full player reads as a deeper, cover-tinted surface without touching the
 * docked strip.
 */
internal fun DrawScope.drawPlayerSheetBackground(
    progress: Float,
    dockedColor: Color,
    expandedColor: Color,
    coverSeed: Color?
) {
    val base = lerpColor(dockedColor, expandedColor, progress)
    if (coverSeed == null) {
        drawRect(base)
        return
    }
    val deepSeed = lerpColor(coverSeed, Color.Black, DeepenAmount)
    drawRect(
        Brush.verticalGradient(
            0f to lerpColor(base, deepSeed, SeedBlendAmount * progress),
            1f to base
        )
    )
}
