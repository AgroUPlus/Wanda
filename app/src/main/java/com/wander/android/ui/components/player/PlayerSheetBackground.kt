package com.wander.android.ui.components.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as lerpColor

/** How dark the cover's colour is taken before tinting. 0 = the seed itself, 1 = black. */
private const val DeepenAmount = 0.25f

/** How much of that colour tints the whole surface at full expansion, over [expandedColor]. */
private const val SeedBlendAmount = 0.22f

/**
 * Draws the player sheet's background: one flat colour, no gradient. A lerp between [dockedColor]
 * and [expandedColor] as the sheet opens, lightly tinted with the cover's seed colour when
 * [coverSeed] is non-null (cover-art theming on).
 *
 * Flat on purpose. A gradient from the cover colour into the plain background left the foot of the
 * screen — where the controls live — reading as the darkest, heaviest part of the player; an even
 * tint keeps the whole surface the same weight.
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
    val tint = lerpColor(coverSeed, Color.Black, DeepenAmount)
    drawRect(lerpColor(base, tint, SeedBlendAmount * progress))
}
