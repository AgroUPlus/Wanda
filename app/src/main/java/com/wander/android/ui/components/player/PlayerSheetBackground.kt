package com.wander.android.ui.components.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as lerpColor

/** How dark the cover's colour is taken before tinting. 0 = the seed itself, 1 = black. */
private const val DeepenAmount = 0.25f

/** How much of that colour tints the surface, docked or expanded alike. */
private const val SeedBlendAmount = 0.22f

/**
 * Draws the player sheet's background: one flat colour, no gradient, and — since [baseColor] is
 * now the same value docked and expanded (see where this is called from `PlayerSheet.kt`) — the
 * same *exact* colour at every point of the drag, not just the same two endpoints.
 *
 * That used to not be true even after the caller's docked/expanded colours were unified: this
 * function still blended in [coverSeed] scaled by how open the sheet was, so a fully docked strip
 * showed none of the tint and a fully open player showed the full 22% of it — the two ends of the
 * same drag, drawn in two different shades of the same colour. Fixed by dropping `progress`
 * entirely: the tint is either on or it isn't, the same amount everywhere, which is what actually
 * makes the strip look like the top of the very surface it grows into rather than a different card
 * sitting in front of it.
 *
 * Flat on purpose. A gradient from the cover colour into the plain background left the foot of the
 * screen — where the controls live — reading as the darkest, heaviest part of the player; an even
 * tint keeps the whole surface the same weight.
 */
internal fun DrawScope.drawPlayerSheetBackground(baseColor: Color, coverSeed: Color?) {
    if (coverSeed == null) {
        drawRect(baseColor)
        return
    }
    val tint = lerpColor(coverSeed, Color.Black, DeepenAmount)
    drawRect(lerpColor(baseColor, tint, SeedBlendAmount))
}
