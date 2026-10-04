package com.wander.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The few colours the expressive screens need that Material has no role for.
 *
 * Derived from whichever scheme is current rather than fixed, so they follow dynamic colour, light
 * mode and a cover-tinted scope like the jam's. Against the blue dark seed they land on the values
 * the design was drawn with.
 *
 * The live greens are the exception: no role means "playing right now", and borrowing one would
 * make that meaning change with the wallpaper. They come in a dark and a light set instead.
 */
@Immutable
internal data class ExtraColors(
    val liveContainer: Color,
    val onLiveContainer: Color,
    val liveDot: Color,
    /** Bars for every rank but the first. */
    val rankBarSecondary: Color,
    /** Decorative shapes on a primary-coloured hero card. */
    val primaryShapeAccent: Color,
    /** An inset panel on a primary-coloured card. */
    val primarySoftPanel: Color,
    /** Secondary text on a primary-coloured card. */
    val onPrimaryVariant: Color,
    /** The track behind a wavy progress indicator on a primary-coloured card. */
    val onPrimaryTrack: Color
)

internal val MaterialTheme.extraColors: ExtraColors
    @Composable get() {
        val scheme = colorScheme
        return remember(scheme) { scheme.extraColors() }
    }

private fun ColorScheme.extraColors(): ExtraColors {
    val dark = surface.luminance() < 0.5f
    return ExtraColors(
        liveContainer = if (dark) Color(0xFF1D3A2C) else Color(0xFFC8EED5),
        onLiveContainer = if (dark) Color(0xFF9FE0B6) else Color(0xFF0F5130),
        liveDot = if (dark) Color(0xFF7FD99F) else Color(0xFF1F8A4C),
        rankBarSecondary = lerp(primary, surfaceContainerHighest, 0.5f),
        primaryShapeAccent = lerp(primary, onPrimary, 0.1f),
        // From the card's own pair, so onPrimary text keeps reading on it. It used to lean on
        // onPrimaryContainer, which no text on it uses: dark under dark text on some covers.
        primarySoftPanel = lerp(primary, onPrimary, 0.14f),
        onPrimaryVariant = lerp(onPrimary, primary, 0.1f),
        onPrimaryTrack = lerp(primary, onPrimary, 0.2f)
    )
}
