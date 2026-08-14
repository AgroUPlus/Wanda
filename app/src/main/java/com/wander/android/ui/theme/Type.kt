package com.wander.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.wander.android.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val PlayfulFont = GoogleFont("Plus Jakarta Sans")

val WandaFontFamily = FontFamily(
    Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.ExtraBold)
)

/**
 * Plus Jakarta Sans has tall ascenders and deep descenders. Compose defaults to trimming the line
 * box to the requested `lineHeight`, and Material's `Tab` positions a label by its last baseline
 * inside a fixed 48 dp slot — between them the descender of a "y" ("Playlists") was being clipped.
 * Centring the glyphs in the line box and trimming nothing keeps every tail intact.
 */
private val FullLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

internal val WandaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = WandaFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
