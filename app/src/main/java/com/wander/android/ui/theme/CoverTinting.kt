package com.wander.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Returns [this] scheme re-coloured around [seed], by [strength] (0 = untouched, 1 = fully
 * applied), respecting [dark].
 *
 * Two intensities, on purpose:
 * - **Accent roles** (primary/secondary/tertiary and their containers) are *derived* from the
 *   seed by tonal blending, so buttons, active states and highlights take the cover's colour.
 * - **Neutral roles** (surfaces, background, outlines, `onSurfaceVariant`) are only *washed*
 *   toward it by a few percent. That is what carries the tint across the rest of the interface —
 *   text, dividers, sheets, cards — without turning the chrome into a second album cover.
 */
@Stable
fun ColorScheme.tintedByCover(seed: Color, strength: Float, dark: Boolean): ColorScheme {
    if (strength <= 0f) return this

    // Blends toward the seed's tonal variant: an accent that reads as the cover's colour.
    fun accent(from: Color, factor: Float) = lerp(from, seed.harmonise(factor, dark), strength)
    // Barely moves: a wash of the seed over a neutral.
    fun wash(from: Color, amount: Float) = lerp(from, seed, amount * strength)

    // The resulting accent colours, computed once so their own "on" colour can be picked from
    // what they actually turned out to be rather than assumed from the theme.
    val newPrimary = accent(primary, if (dark) 0.60f else 0.50f)
    val newSecondary = accent(secondary, 0.40f)
    val newTertiary = accent(tertiary, if (dark) 0.50f else 0.60f)

    return copy(
        primary              = newPrimary,
        onPrimary            = lerp(onPrimary, newPrimary.contrastingOnColor(), strength),
        primaryContainer     = accent(primaryContainer,     if (dark) 0.25f else 0.90f),
        onPrimaryContainer   = accent(onPrimaryContainer,   if (dark) 0.90f else 0.10f),
        secondary            = newSecondary,
        onSecondary          = lerp(onSecondary, newSecondary.contrastingOnColor(), strength),
        secondaryContainer   = accent(secondaryContainer,   if (dark) 0.20f else 0.85f),
        onSecondaryContainer = accent(onSecondaryContainer, if (dark) 0.85f else 0.15f),
        tertiary             = newTertiary,
        onTertiary           = lerp(onTertiary, newTertiary.contrastingOnColor(), strength),
        tertiaryContainer    = accent(tertiaryContainer,    if (dark) 0.30f else 0.88f),
        onTertiaryContainer  = accent(onTertiaryContainer,  if (dark) 0.88f else 0.12f),

        background              = wash(background,              0.06f),
        onBackground            = wash(onBackground,            0.05f),
        surface                 = wash(surface,                 0.06f),
        onSurface               = wash(onSurface,               0.05f),
        surfaceVariant          = wash(surfaceVariant,          0.10f),
        onSurfaceVariant        = wash(onSurfaceVariant,        0.12f),
        surfaceDim              = wash(surfaceDim,              0.08f),
        surfaceBright           = wash(surfaceBright,           0.08f),
        surfaceContainerLowest  = wash(surfaceContainerLowest,  0.08f),
        surfaceContainerLow     = wash(surfaceContainerLow,     0.08f),
        surfaceContainer        = wash(surfaceContainer,        0.08f),
        surfaceContainerHigh    = wash(surfaceContainerHigh,    0.08f),
        surfaceContainerHighest = wash(surfaceContainerHighest, 0.08f),
        outline                 = wash(outline,                 0.14f),
        outlineVariant          = wash(outlineVariant,          0.12f),
        surfaceTint             = accent(surfaceTint,           if (dark) 0.60f else 0.50f),
    )
}

/**
 * Black or white text for [this] background, picked from what the colour actually turned out to
 * be rather than assumed from the theme's own dark/light mode.
 */
private fun Color.contrastingOnColor(): Color =
    if (luminance() > 0.5f) Color.Black else Color.White

/**
 * Blends [this] colour toward white (light) or black (dark) by [factor] to produce a tonal
 * variant. Gives a convincing Material-ish result without the full MCU library.
 */
private fun Color.harmonise(factor: Float, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    return Color(
        red   = red   + (target.red   - red)   * factor,
        green = green + (target.green - green) * factor,
        blue  = blue  + (target.blue  - blue)  * factor,
        alpha = 1f
    )
}

/**
 * Pins the darkest surfaces to true black for OLED panels.
 * Internal so [Theme.kt] can also call it from [WanderTheme].
 */
internal fun ColorScheme.toAmoled(): ColorScheme = copy(
    background              = Color.Black,
    surface                 = Color.Black,
    surfaceDim              = Color.Black,
    surfaceContainerLowest  = Color.Black,
    surfaceContainerLow     = Color(0xFF0C0B12),
    surfaceContainer        = Color(0xFF14131C),
    surfaceContainerHigh    = Color(0xFF1C1B26),
    surfaceContainerHighest = Color(0xFF242330)
)
