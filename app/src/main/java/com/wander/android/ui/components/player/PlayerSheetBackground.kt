package com.wander.android.ui.components.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp

/** How dark the cover's colour is taken before tinting. 0 = the seed itself, 1 = black. */
private const val DeepenAmount = 0.08f

/** How much of that colour tints the surface, docked or expanded alike — the base, flat layer. */
private const val SeedBlendAmount = 0.42f

/** How far toward white the seed is lifted for the ambient scrim's own two colour stops. */
private const val LiftAmount = 0.32f

/** How strongly the scrim's brightest stop reads over the flat base. Deliberately subtle. */
private const val ScrimBlendAmount = 0.16f

/**
 * How far the scrim's centre drifts side to side across the sheet's width, as a fraction of it —
 * small on purpose: this is meant to read as the surface breathing, not as a spotlight sweeping.
 */
private const val DriftRangeFraction = 0.22f

/**
 * Draws the player sheet's background: a flat cover-tinted base, since [baseColor] is now the same
 * value docked and expanded (see where this is called from `PlayerSheet.kt`) — the same *exact*
 * base colour at every point of the drag, not just the same two endpoints — plus an ambient scrim
 * on top of it: a two-stop radial gradient, built from the same [coverSeed], whose centre drifts
 * slowly across the top of the sheet as [seekProgress] advances.
 *
 * The base flat fill is load-bearing on its own and must stay exactly that: a gradient standing in
 * for it once left the foot of the screen — where the controls live — reading as the darkest,
 * heaviest part of the player, which is why it was pulled back out from here before. The scrim is
 * additive on top of that unconditionally flat base rather than a replacement for it, is capped at
 * [ScrimBlendAmount] so it never dominates the surface, and fades to fully transparent well short of
 * the sheet's bottom edge — the base colour is what the player's controls always sit on.
 */
internal fun DrawScope.drawPlayerSheetBackground(baseColor: Color, coverSeed: Color?, seekProgress: Float) {
    if (coverSeed == null) {
        drawRect(baseColor)
        return
    }
    val tint = lerpColor(coverSeed, Color.Black, DeepenAmount)
    val flat = lerpColor(baseColor, tint, SeedBlendAmount)
    drawRect(flat)

    val bright = lerpColor(coverSeed, Color.White, LiftAmount)
    val driftX = lerp(0.5f - DriftRangeFraction / 2f, 0.5f + DriftRangeFraction / 2f, seekProgress.coerceIn(0f, 1f))
    drawRect(
        brush = Brush.radialGradient(
            // Three stops, not two: a single fade from `bright` to transparent reads as one flat
            // spot with a hard-ish edge at this size, where a mid stop keeps the middle of the
            // gradient's own falloff instead of a straight fade holding the same brightness too long.
            colorStops = arrayOf(
                0f to lerpColor(flat, bright, ScrimBlendAmount),
                0.55f to lerpColor(flat, bright, ScrimBlendAmount * 0.35f),
                1f to Color.Transparent
            ),
            center = Offset(size.width * driftX, 0f),
            radius = size.width.coerceAtLeast(1f)
        )
    )
}
