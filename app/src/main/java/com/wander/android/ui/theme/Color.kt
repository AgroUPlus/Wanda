package com.wander.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback palette for devices without Monet dynamic colour (below API 31, or when the user
 * turns it off). Seeded from a violet/teal pair that stays legible against album artwork.
 */
private val Violet = Color(0xFF6D5DF6)
private val VioletDark = Color(0xFFC4BFFF)
private val Teal = Color(0xFF00A3A3)
private val TealDark = Color(0xFF66D9D9)
private val Coral = Color(0xFFE8577D)
private val CoralDark = Color(0xFFFFB1C3)

/**
 * The dot on anything happening live — a jam in progress, a listen-along in the bar.
 *
 * Outside the scheme on purpose, and the same in both themes: it is a recording light, and the one
 * colour read as "on air" without a label beside it. Taking `error` instead would tie it to
 * whatever the palette does with failure, and under Monet that is a wallpaper-derived red that can
 * land anywhere from rust to pink.
 */
internal val LiveIndicator = Color(0xFFEF4444)

/**
 * Content drawn straight onto cover art rather than onto a surface.
 *
 * The immersive player has no surface under its text — the artwork is the background, behind a
 * scrim dark enough to carry white. `onSurface` would follow the theme into near-black under a
 * light scheme and disappear into the scrim, so this deliberately does not track the scheme; the
 * scrim is what guarantees the contrast, and it is drawn to suit this.
 */
internal val OnCoverArt = Color.White

internal val WandaLightScheme = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF1B0A6B),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF6F6),
    onSecondaryContainer = Color(0xFF00201F),
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E1),
    onTertiaryContainer = Color(0xFF3E0018)
)

internal val WandaDarkScheme = darkColorScheme(
    primary = VioletDark,
    onPrimary = Color(0xFF2A1A8F),
    primaryContainer = Color(0xFF4034C9),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = TealDark,
    onSecondary = Color(0xFF003736),
    secondaryContainer = Color(0xFF00504E),
    onSecondaryContainer = Color(0xFFCFF6F6),
    tertiary = CoralDark,
    onTertiary = Color(0xFF61001F),
    tertiaryContainer = Color(0xFF8A2F4C),
    onTertiaryContainer = Color(0xFFFFD9E1)
)
