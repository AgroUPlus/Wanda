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

/**
 * One vivid, distinct colour per music source, used for [com.wander.android.ui.components.SourceIcon]
 * everywhere a source needs to be told apart at a glance (filter chips, the source picker, the
 * Connections rows). Deliberately outside `colorScheme` for the same reason [LiveIndicator] is — a
 * source's identity should not shift with the theme.
 *
 * Navidrome/Subsonic gets blue rather than its own logo's green: this source speaks the general
 * Subsonic protocol, not Navidrome exclusively, so its badge colour stands for "a self-hosted
 * server" rather than one specific project's branding.
 */
internal val NavidromeBlue = Color(0xFF0EA5E9)
internal val YtMusicCoral = Color(0xFFF43F5E)
internal val LocalDeviceAmber = Color(0xFFFB923C)

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
    onTertiaryContainer = Color(0xFF3E0018),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1B1A22),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1B1A22),
    surfaceVariant = Color(0xFFE5E0F2),
    onSurfaceVariant = Color(0xFF474553),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2FA),
    surfaceContainer = Color(0xFFEEEBF4),
    surfaceContainerHigh = Color(0xFFE8E5EE),
    surfaceContainerHighest = Color(0xFFE2DFE9),
    surfaceDim = Color(0xFFDCD8E2),
    surfaceBright = Color(0xFFFAF8FF),
    outline = Color(0xFF787584),
    outlineVariant = Color(0xFFC9C4D5)
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
    onTertiaryContainer = Color(0xFFFFD9E1),
    background = Color(0xFF13121A),
    onBackground = Color(0xFFE5E1EC),
    surface = Color(0xFF13121A),
    onSurface = Color(0xFFE5E1EC),
    surfaceVariant = Color(0xFF484554),
    onSurfaceVariant = Color(0xFFC9C4D5),
    surfaceContainerLowest = Color(0xFF0E0D14),
    surfaceContainerLow = Color(0xFF1B1A23),
    surfaceContainer = Color(0xFF201F29),
    surfaceContainerHigh = Color(0xFF2B2934),
    surfaceContainerHighest = Color(0xFF36343F),
    surfaceDim = Color(0xFF13121A),
    surfaceBright = Color(0xFF3A3844),
    outline = Color(0xFF938F9F),
    outlineVariant = Color(0xFF484554)
)

