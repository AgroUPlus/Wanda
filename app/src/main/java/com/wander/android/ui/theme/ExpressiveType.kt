package com.wander.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The expressive display and list styles the social and statistics screens are built from.
 *
 * Derived from [WandaTypography] rather than declared beside it so they keep its rounded title face
 * and its untrimmed line boxes. The design asks for per-style width as well, but the bundled Google
 * Sans Flex subset carries only the weight and roundness axes, so width is left at the default.
 */

/** A screen's own name: Friends, Jam, Circle. */
internal val Typography.screenTitle: TextStyle
    get() = displayLarge.copy(fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)

/** A person's name at the top of their profile. */
internal val Typography.profileName: TextStyle
    get() = displayLarge.copy(fontSize = 52.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)

/** The big title on a hero card. */
internal val Typography.heroTitle: TextStyle
    get() = displayLarge.copy(fontSize = 32.sp, lineHeight = 1.05.em, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp)

internal val Typography.heroTitleSmall: TextStyle
    get() = heroTitle.copy(fontSize = 28.sp)

/** A jam's join code, spaced out to be read aloud. */
internal val Typography.joinCode: TextStyle
    get() = displayLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)

/** The number on a square stat tile. */
internal val Typography.displayStat: TextStyle
    get() = displayLarge.copy(fontSize = 64.sp, lineHeight = 0.9.em, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)

internal val Typography.displayStatHuge: TextStyle
    get() = displayStat.copy(fontSize = 88.sp, lineHeight = 0.85.em, letterSpacing = (-3).sp)

internal val Typography.displayStatMid: TextStyle
    get() = displayStat.copy(fontSize = 56.sp)

internal val Typography.displayStatTime: TextStyle
    get() = displayStat.copy(fontSize = 38.sp, lineHeight = 0.95.em, letterSpacing = (-1).sp)

/** Every section header and card title on these screens. */
internal val Typography.sectionTitle: TextStyle
    get() = headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)

internal val Typography.listTitle: TextStyle
    get() = titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)

internal val Typography.listSupporting: TextStyle
    get() = bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)

internal val Typography.buttonLarge: TextStyle
    get() = labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

internal val Typography.buttonSmall: TextStyle
    get() = labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

/** LISTENING, JOIN CODE and the like. */
internal val Typography.overline: TextStyle
    get() = labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)

internal val Typography.matchPercent: TextStyle
    get() = displayLarge.copy(fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp)

internal val Typography.trendBadge: TextStyle
    get() = displayLarge.copy(fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp)
