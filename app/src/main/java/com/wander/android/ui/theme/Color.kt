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
