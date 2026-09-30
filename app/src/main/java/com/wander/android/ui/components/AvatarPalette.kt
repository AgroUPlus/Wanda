package com.wander.android.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * Deterministic palettes for cute procedural avatars.
 * Each entry has: (Gradient Start, Gradient End, Accent/Blush, Feature/Eye color, Accessory color)
 */
internal data class AvatarPalette(
    val bgStart: Color,
    val bgEnd: Color,
    val blush: Color,
    val featureColor: Color,
    val accessoryColor: Color
)

internal val AVATAR_PALETTES = listOf(
    // 1. Strawberry Peach
    AvatarPalette(
        bgStart = Color(0xFFFFB4A2),
        bgEnd = Color(0xFFFFCDB2),
        blush = Color(0xFFE56B6F),
        featureColor = Color(0xFF4A2828),
        accessoryColor = Color(0xFFB5838D)
    ),
    // 2. Mint & Sage
    AvatarPalette(
        bgStart = Color(0xFFB7E4C7),
        bgEnd = Color(0xFFD8F3DC),
        blush = Color(0xFF74C69D),
        featureColor = Color(0xFF1B4332),
        accessoryColor = Color(0xFF52B788)
    ),
    // 3. Lavender & Lilac
    AvatarPalette(
        bgStart = Color(0xFFD8B4E2),
        bgEnd = Color(0xFFE8D7F1),
        blush = Color(0xFFC77DFF),
        featureColor = Color(0xFF3C096C),
        accessoryColor = Color(0xFF9D4EDD)
    ),
    // 4. Sky & Cloud
    AvatarPalette(
        bgStart = Color(0xFFA2D2FF),
        bgEnd = Color(0xFFBDE0FE),
        blush = Color(0xFFFFC8DD),
        featureColor = Color(0xFF1D3557),
        accessoryColor = Color(0xFF457B9D)
    ),
    // 5. Sunset Melon
    AvatarPalette(
        bgStart = Color(0xFFFFB703),
        bgEnd = Color(0xFFFFD166),
        blush = Color(0xFFEF476F),
        featureColor = Color(0xFF3D2600),
        accessoryColor = Color(0xFFFB8500)
    ),
    // 6. Cotton Candy
    AvatarPalette(
        bgStart = Color(0xFFFFC6FF),
        bgEnd = Color(0xFFBDB2FF),
        blush = Color(0xFFFF70A6),
        featureColor = Color(0xFF3A0CA3),
        accessoryColor = Color(0xFF7209B7)
    ),
    // 7. Matcha Green Tea
    AvatarPalette(
        bgStart = Color(0xFFCCE3DE),
        bgEnd = Color(0xFFEAF4F4),
        blush = Color(0xFFA4C3B2),
        featureColor = Color(0xFF283618),
        accessoryColor = Color(0xFF6B9080)
    ),
    // 8. Warm Honey
    AvatarPalette(
        bgStart = Color(0xFFFDE4CF),
        bgEnd = Color(0xFFFFCAD4),
        blush = Color(0xFFF4ACB7),
        featureColor = Color(0xFF4A3E3D),
        accessoryColor = Color(0xFF9D8189)
    ),
    // 9. Electric Violet
    AvatarPalette(
        bgStart = Color(0xFFC8B6FF),
        bgEnd = Color(0xFFE7C6FF),
        blush = Color(0xFFFF85A1),
        featureColor = Color(0xFF240046),
        accessoryColor = Color(0xFF7B2CBF)
    ),
    // 10. Aqua Marine
    AvatarPalette(
        bgStart = Color(0xFF90E0EF),
        bgEnd = Color(0xFFCAF0F8),
        blush = Color(0xFFFF9EAA),
        featureColor = Color(0xFF03045E),
        accessoryColor = Color(0xFF0077B6)
    ),
    // 11. Coral Blossom
    AvatarPalette(
        bgStart = Color(0xFFF4A261),
        bgEnd = Color(0xFFE76F51),
        blush = Color(0xFFD62828),
        featureColor = Color(0xFF2B2D42),
        accessoryColor = Color(0xFFE9C46A)
    ),
    // 12. Soft Pistachio
    AvatarPalette(
        bgStart = Color(0xFFD4E09B),
        bgEnd = Color(0xFFF6F4D2),
        blush = Color(0xFFCBDFBD),
        featureColor = Color(0xFF333D29),
        accessoryColor = Color(0xFFA4AC86)
    )
)

/** The one hash, so a seed's palette is the same wherever it is asked for. */
internal fun seedHash(seed: String): Long =
    seed.lowercase().fold(0L) { acc, c -> (acc * 37L + c.code) and 0x7FFFFFFFFFFFFFFFL }

/**
 * The two background colours of [seed]'s avatar, for painting something larger in their colours.
 */
internal fun avatarGradient(seed: String): Pair<Color, Color> {
    val clean = seed.trim()
    val palette = if (clean.isEmpty()) {
        AVATAR_PALETTES[0]
    } else {
        AVATAR_PALETTES[(seedHash(clean) % AVATAR_PALETTES.size).toInt().absoluteValue]
    }
    return palette.bgStart to palette.bgEnd
}
