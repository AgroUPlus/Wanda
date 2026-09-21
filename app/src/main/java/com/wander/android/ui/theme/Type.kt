package com.wander.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.wander.android.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

/**
 * The same variable font Material 3 Expressive's own "Rounded" style is built from — Google made
 * it public (OFL) in late 2025. Requesting it by weight through the downloadable-fonts provider,
 * same as the font it replaces; the provider serves the family's own default instance on its
 * roundness/grade/width axes rather than anything Compose can dial in per-weight.
 */
private val PlayfulFont = GoogleFont("Google Sans Flex")

val WandaFontFamily = FontFamily(
    androidx.compose.ui.text.googlefonts.Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    androidx.compose.ui.text.googlefonts.Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    androidx.compose.ui.text.googlefonts.Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    androidx.compose.ui.text.googlefonts.Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    androidx.compose.ui.text.googlefonts.Font(googleFont = PlayfulFont, fontProvider = fontProvider, weight = FontWeight.ExtraBold)
)

/**
 * Titles get their roundness from a *bundled* copy of the same font instead: the downloadable
 * provider above serves only the family's default instance on its variation axes, with no way for
 * Compose to dial `ROND` in per style, so the axis this app actually wants is unreachable through
 * it. `res/font/google_sans_flex.ttf` is the family's Latin static+variable subset (OFL-licensed,
 * same as the downloadable copy), which `FontVariation.Settings` can push on `ROND` directly.
 *
 * Only the weights the title/headline styles below actually use — dialing in every weight the
 * downloadable family carries was never needed for a handful of styles.
 */
private fun roundedFont(weight: FontWeight) = Font(
    resId = R.font.google_sans_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", TitleRoundness)
    )
)

/**
 * How far up the family's 0–100 `ROND` axis titles sit. Short of the maximum: full roundness on a
 * *bold* weight starts eating into letterform distinctiveness at these sizes, and the brief asked
 * for noticeably rounder, not cartoonish. Worth a visual pass in a running build before shipping.
 */
private const val TitleRoundness = 65f

private val WandaRoundedFontFamily = FontFamily(
    roundedFont(FontWeight.Medium),
    roundedFont(FontWeight.SemiBold),
    roundedFont(FontWeight.Bold),
    roundedFont(FontWeight.ExtraBold)
)

/**
 * Compose defaults to trimming the line box to the requested `lineHeight`, and Material's `Tab`
 * positions a label by its last baseline inside a fixed 48 dp slot — between them the descender of
 * a tall-descender glyph ("Playlists") was being clipped. Centring the glyphs in the line box and
 * trimming nothing keeps every tail intact regardless of which font is loaded.
 */
private val FullLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

internal val WandaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = WandaRoundedFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 54.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = WandaRoundedFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = WandaRoundedFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = WandaRoundedFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = WandaRoundedFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WandaRoundedFontFamily,
        lineHeightStyle = FullLineHeight,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = WandaRoundedFontFamily,
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
