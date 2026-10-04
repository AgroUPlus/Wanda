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
 *   toward it, by about a tenth. That is what carries the tint across the rest of the interface —
 *   text, dividers, sheets, cards — without turning the chrome into a second album cover.
 *
 * The wash strengths are what the Jam screen used to reach by tinting an already tinted scheme a
 * second time; that look is now every screen's, from one pass (see [CoverTintedTheme]).
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
    val newPrimaryContainer = accent(primaryContainer, if (dark) 0.25f else 0.90f)
    val newSecondaryContainer = accent(secondaryContainer, if (dark) 0.20f else 0.85f)
    val newTertiaryContainer = accent(tertiaryContainer, if (dark) 0.30f else 0.88f)
    // The seed-derived text colour where it stays readable on its container, black or white where
    // a bright or dull cover would have left it washed out against it.
    fun onContainer(container: Color, from: Color, factor: Float) =
        lerp(from, seed.harmonise(factor, dark).readableOn(container), strength)

    return copy(
        primary              = newPrimary,
        onPrimary            = lerp(onPrimary, newPrimary.contrastingOnColor(), strength),
        primaryContainer     = newPrimaryContainer,
        onPrimaryContainer   = onContainer(newPrimaryContainer, onPrimaryContainer, if (dark) 0.90f else 0.10f),
        secondary            = newSecondary,
        onSecondary          = lerp(onSecondary, newSecondary.contrastingOnColor(), strength),
        secondaryContainer   = newSecondaryContainer,
        onSecondaryContainer = onContainer(newSecondaryContainer, onSecondaryContainer, if (dark) 0.85f else 0.15f),
        tertiary             = newTertiary,
        onTertiary           = lerp(onTertiary, newTertiary.contrastingOnColor(), strength),
        tertiaryContainer    = newTertiaryContainer,
        onTertiaryContainer  = onContainer(newTertiaryContainer, onTertiaryContainer, if (dark) 0.88f else 0.12f),

        background              = wash(background,              0.12f),
        onBackground            = wash(onBackground,            0.10f),
        surface                 = wash(surface,                 0.12f),
        onSurface               = wash(onSurface,               0.10f),
        surfaceVariant          = wash(surfaceVariant,          0.19f),
        onSurfaceVariant        = wash(onSurfaceVariant,        0.23f),
        surfaceDim              = wash(surfaceDim,              0.15f),
        surfaceBright           = wash(surfaceBright,           0.15f),
        surfaceContainerLowest  = wash(surfaceContainerLowest,  0.15f),
        surfaceContainerLow     = wash(surfaceContainerLow,     0.15f),
        surfaceContainer        = wash(surfaceContainer,        0.15f),
        surfaceContainerHigh    = wash(surfaceContainerHigh,    0.15f),
        surfaceContainerHighest = wash(surfaceContainerHighest, 0.15f),
        outline                 = wash(outline,                 0.26f),
        outlineVariant          = wash(outlineVariant,          0.23f),
        surfaceTint             = accent(surfaceTint,           if (dark) 0.60f else 0.50f),
    )
}

/**
 * Black or white text for [this] background, picked from what the colour actually turned out to
 * be rather than assumed from the theme's own dark/light mode.
 *
 * The switch sits where black and white contrast equally, not at mid-grey: a background at 30%
 * luminance gives white only 3:1 but black 7:1, and those mid-bright covers are the common case.
 */
private fun Color.contrastingOnColor(): Color =
    if (luminance() > EQUAL_CONTRAST_LUMINANCE) Color.Black else Color.White

/** sqrt(1.05 × 0.05) − 0.05: the luminance at which black and white text contrast equally. */
private const val EQUAL_CONTRAST_LUMINANCE = 0.179f

/**
 * [this] where it reaches WCAG AA body-text contrast (4.5:1) against [background], otherwise
 * whichever of black or white does.
 */
private fun Color.readableOn(background: Color): Color =
    if (contrastRatio(this, background) >= MIN_TEXT_CONTRAST) this else background.contrastingOnColor()

private fun contrastRatio(a: Color, b: Color): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + 0.05f) / (dark + 0.05f)
}

private const val MIN_TEXT_CONTRAST = 4.5f

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
