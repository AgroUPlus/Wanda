package com.wander.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

/**
 * Deterministic palettes for cute procedural avatars.
 * Each entry has: (Gradient Start, Gradient End, Accent/Blush, Feature/Eye color)
 */
private data class AvatarPalette(
    val bgStart: Color,
    val bgEnd: Color,
    val blush: Color,
    val featureColor: Color,
    val accessoryColor: Color
)

private val AVATAR_PALETTES = listOf(
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

/**
 * Procedural cute avatar / identicon.
 * When [avatarUrl] is provided, loads the remote image.
 * Otherwise, renders a deterministic, cheerful character based on [seed].
 */
@Composable
internal fun CuteAvatar(
    seed: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    size: Dp = 40.dp,
    showBorder: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.surface
) {
    val cleanSeed = seed.trim().ifEmpty { "wanderer" }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBorder) Modifier.border(2.dp, borderColor, CircleShape)
                else Modifier
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = cleanSeed,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape)
            )
        } else {
            ProceduralCanvasAvatar(seed = cleanSeed, size = size)
        }
    }
}

@Composable
private fun ProceduralCanvasAvatar(
    seed: String,
    size: Dp
) {
    val hash = remember(seed) {
        seed.lowercase().fold(0L) { acc, c -> (acc * 37L + c.code) and 0x7FFFFFFFFFFFFFFFL }
    }

    val paletteIndex = (hash % AVATAR_PALETTES.size).toInt().absoluteValue
    val palette = AVATAR_PALETTES[paletteIndex]

    // Feature variants derived from bits of the hash
    val eyeStyle = ((hash shr 4) % 5).toInt().absoluteValue
    val mouthStyle = ((hash shr 7) % 4).toInt().absoluteValue
    val accessoryStyle = ((hash shr 10) % 5).toInt().absoluteValue
    val hasFreckles = ((hash shr 13) % 3) == 0L

    Canvas(modifier = Modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val center = Offset(width / 2f, height / 2f)

        // 1. Soft Gradient Background
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(palette.bgStart, palette.bgEnd),
                start = Offset(0f, 0f),
                end = Offset(width, height)
            ),
            radius = width / 2f,
            center = center
        )

        // 2. Cute Accessory (Ears, Sprout, Halo, Sparkle)
        drawAccessory(accessoryStyle, palette.accessoryColor, width, height)

        // 3. Blush Cheeks
        val cheekY = height * 0.58f
        val cheekRadius = width * 0.09f
        drawCircle(
            color = palette.blush.copy(alpha = 0.65f),
            radius = cheekRadius,
            center = Offset(width * 0.26f, cheekY)
        )
        drawCircle(
            color = palette.blush.copy(alpha = 0.65f),
            radius = cheekRadius,
            center = Offset(width * 0.74f, cheekY)
        )

        // Optional Freckles
        if (hasFreckles) {
            val fColor = palette.featureColor.copy(alpha = 0.35f)
            val fRadius = width * 0.015f
            drawCircle(fColor, fRadius, Offset(width * 0.28f, cheekY - height * 0.04f))
            drawCircle(fColor, fRadius, Offset(width * 0.32f, cheekY - height * 0.02f))
            drawCircle(fColor, fRadius, Offset(width * 0.72f, cheekY - height * 0.04f))
            drawCircle(fColor, fRadius, Offset(width * 0.68f, cheekY - height * 0.02f))
        }

        // 4. Cute Eyes
        val eyeY = height * 0.44f
        val eyeLeftX = width * 0.33f
        val eyeRightX = width * 0.67f
        drawEyes(eyeStyle, palette.featureColor, eyeLeftX, eyeRightX, eyeY, width)

        // 5. Cute Mouth
        val mouthY = height * 0.58f
        drawMouth(mouthStyle, palette.featureColor, center.x, mouthY, width)
    }
}

private fun DrawScope.drawEyes(
    style: Int,
    color: Color,
    leftX: Float,
    rightX: Float,
    y: Float,
    size: Float
) {
    val strokeWidth = size * 0.055f
    when (style % 5) {
        // 0. Cute Classic Large Sparkle Dot Eyes with double highlights (◕ ◕)
        0 -> {
            val eyeRadius = size * 0.07f
            drawCircle(color = color, radius = eyeRadius, center = Offset(leftX, y))
            drawCircle(color = color, radius = eyeRadius, center = Offset(rightX, y))
            // Big glint
            val glintRadius = size * 0.024f
            drawCircle(color = Color.White, radius = glintRadius, center = Offset(leftX + eyeRadius * 0.3f, y - eyeRadius * 0.3f))
            drawCircle(color = Color.White, radius = glintRadius, center = Offset(rightX + eyeRadius * 0.3f, y - eyeRadius * 0.3f))
            // Mini secondary glint
            val miniGlint = size * 0.012f
            drawCircle(color = Color.White, radius = miniGlint, center = Offset(leftX - eyeRadius * 0.25f, y + eyeRadius * 0.3f))
            drawCircle(color = Color.White, radius = miniGlint, center = Offset(rightX - eyeRadius * 0.25f, y + eyeRadius * 0.3f))
        }
        // 1. Pure Happy Curved Closed Smile Eyes (^ ^)
        1 -> {
            val r = size * 0.075f
            val pathLeft = Path().apply {
                moveTo(leftX - r, y + r * 0.25f)
                quadraticTo(leftX, y - r * 0.9f, leftX + r, y + r * 0.25f)
            }
            val pathRight = Path().apply {
                moveTo(rightX - r, y + r * 0.25f)
                quadraticTo(rightX, y - r * 0.9f, rightX + r, y + r * 0.25f)
            }
            drawPath(pathLeft, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawPath(pathRight, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        // 2. Playful Wink with Star (> ◕)
        2 -> {
            val r = size * 0.07f
            // Left wink arc
            val winkPath = Path().apply {
                moveTo(leftX - r, y - r * 0.3f)
                lineTo(leftX, y)
                lineTo(leftX - r, y + r * 0.3f)
            }
            drawPath(winkPath, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            // Right big smiling eye
            drawCircle(color = color, radius = r, center = Offset(rightX, y))
            drawCircle(color = Color.White, radius = r * 0.35f, center = Offset(rightX + r * 0.3f, y - r * 0.3f))
            drawCircle(color = Color.White, radius = r * 0.18f, center = Offset(rightX - r * 0.2f, y + r * 0.3f))
        }
        // 3. Cheerful Sparkle Eyes (★ ★)
        3 -> {
            fun drawStarEye(cx: Float, cy: Float, radius: Float) {
                val p = Path().apply {
                    moveTo(cx, cy - radius)
                    quadraticTo(cx, cy, cx + radius, cy)
                    quadraticTo(cx, cy, cx, cy + radius)
                    quadraticTo(cx, cy, cx - radius, cy)
                    quadraticTo(cx, cy, cx, cy - radius)
                }
                drawPath(p, color = color)
                drawCircle(Color.White, radius * 0.28f, Offset(cx + radius * 0.25f, cy - radius * 0.25f))
            }
            drawStarEye(leftX, y, size * 0.08f)
            drawStarEye(rightX, y, size * 0.08f)
        }
        // 4. Sweet Joyful Rounded Smiling Eyes (◕ ◕)
        else -> {
            val ovalW = size * 0.09f
            val ovalH = size * 0.12f
            drawRoundRect(
                color = color,
                topLeft = Offset(leftX - ovalW / 2, y - ovalH / 2),
                size = Size(ovalW, ovalH),
                cornerRadius = CornerRadius(ovalW / 2, ovalW / 2)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(rightX - ovalW / 2, y - ovalH / 2),
                size = Size(ovalW, ovalH),
                cornerRadius = CornerRadius(ovalW / 2, ovalW / 2)
            )
            // Cheerful glints
            drawCircle(color = Color.White, radius = size * 0.025f, center = Offset(leftX + ovalW * 0.15f, y - ovalH * 0.2f))
            drawCircle(color = Color.White, radius = size * 0.025f, center = Offset(rightX + ovalW * 0.15f, y - ovalH * 0.2f))
            drawCircle(color = Color.White, radius = size * 0.012f, center = Offset(leftX - ovalW * 0.15f, y + ovalH * 0.2f))
            drawCircle(color = Color.White, radius = size * 0.012f, center = Offset(rightX - ovalW * 0.15f, y + ovalH * 0.2f))
        }
    }
}

private fun DrawScope.drawMouth(
    style: Int,
    color: Color,
    centerX: Float,
    y: Float,
    size: Float
) {
    val strokeWidth = size * 0.05f
    when (style % 4) {
        // 0. Sweet Gentle Curved Smile (‿)
        0 -> {
            val w = size * 0.11f
            val h = size * 0.07f
            val path = Path().apply {
                moveTo(centerX - w, y)
                quadraticTo(centerX, y + h, centerX + w, y)
            }
            drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        // 1. Big Happy Open Smile with Pink Tongue (D)
        1 -> {
            val w = size * 0.12f
            val h = size * 0.09f
            val mouthPath = Path().apply {
                moveTo(centerX - w, y)
                quadraticTo(centerX, y + h * 1.6f, centerX + w, y)
                close()
            }
            drawPath(mouthPath, color = color)
            // Cute pink tongue
            val tonguePath = Path().apply {
                moveTo(centerX - w * 0.6f, y + h * 0.5f)
                quadraticTo(centerX, y + h * 1.4f, centerX + w * 0.6f, y + h * 0.5f)
                close()
            }
            drawPath(tonguePath, color = Color(0xFFFF758F))
        }
        // 2. Cute Cat / Bunny Smile (3)
        2 -> {
            val w = size * 0.065f
            val h = size * 0.05f
            val pathLeft = Path().apply {
                moveTo(centerX - w * 2, y)
                quadraticTo(centerX - w, y + h, centerX, y)
            }
            val pathRight = Path().apply {
                moveTo(centerX, y)
                quadraticTo(centerX + w, y + h, centerX + w * 2, y)
            }
            drawPath(pathLeft, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawPath(pathRight, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
        // 3. Radiant Beaming Joy Smile ( ᗨ )
        else -> {
            val w = size * 0.13f
            val h = size * 0.08f
            val path = Path().apply {
                moveTo(centerX - w, y - h * 0.2f)
                quadraticTo(centerX - w * 0.5f, y + h, centerX, y + h)
                quadraticTo(centerX + w * 0.5f, y + h, centerX + w, y - h * 0.2f)
            }
            drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawAccessory(
    style: Int,
    color: Color,
    width: Float,
    height: Float
) {
    when (style % 5) {
        // 0. Cute Bear / Cat Ears
        0 -> {
            val earRadius = width * 0.13f
            drawCircle(color = color.copy(alpha = 0.55f), radius = earRadius, center = Offset(width * 0.22f, height * 0.20f))
            drawCircle(color = color.copy(alpha = 0.55f), radius = earRadius, center = Offset(width * 0.78f, height * 0.20f))
            // Inner ear
            drawCircle(color = Color.White.copy(alpha = 0.4f), radius = earRadius * 0.55f, center = Offset(width * 0.22f, height * 0.20f))
            drawCircle(color = Color.White.copy(alpha = 0.4f), radius = earRadius * 0.55f, center = Offset(width * 0.78f, height * 0.20f))
        }
        // 1. Cute Little Sprout 🌱
        1 -> {
            val stemPath = Path().apply {
                moveTo(width * 0.5f, height * 0.24f)
                quadraticTo(width * 0.48f, height * 0.15f, width * 0.5f, height * 0.08f)
            }
            drawPath(stemPath, color = color, style = Stroke(width = width * 0.04f, cap = StrokeCap.Round))
            val leafPath = Path().apply {
                moveTo(width * 0.5f, height * 0.09f)
                quadraticTo(width * 0.66f, height * 0.04f, width * 0.63f, height * 0.14f)
                quadraticTo(width * 0.54f, height * 0.14f, width * 0.5f, height * 0.09f)
            }
            drawPath(leafPath, color = color)
        }
        // 2. Cute Sparkle Star ✨
        2 -> {
            val sx = width * 0.78f
            val sy = height * 0.24f
            val sRadius = width * 0.065f
            val sparkPath = Path().apply {
                moveTo(sx, sy - sRadius)
                quadraticTo(sx, sy, sx + sRadius, sy)
                quadraticTo(sx, sy, sx, sy + sRadius)
                quadraticTo(sx, sy, sx - sRadius, sy)
                quadraticTo(sx, sy, sx, sy - sRadius)
            }
            drawPath(sparkPath, color = color.copy(alpha = 0.85f))
        }
        // 3. Angelic Glow Halo 😇
        3 -> {
            val haloRect = Path().apply {
                moveTo(width * 0.35f, height * 0.14f)
                quadraticTo(width * 0.5f, height * 0.08f, width * 0.65f, height * 0.14f)
                quadraticTo(width * 0.5f, height * 0.20f, width * 0.35f, height * 0.14f)
            }
            drawPath(haloRect, color = color.copy(alpha = 0.7f), style = Stroke(width = width * 0.035f, cap = StrokeCap.Round))
        }
        // 4. Soft Sweet Starlet Pair ✨
        else -> {
            val sRadius = width * 0.045f
            fun drawMiniStar(sx: Float, sy: Float) {
                val p = Path().apply {
                    moveTo(sx, sy - sRadius)
                    quadraticTo(sx, sy, sx + sRadius, sy)
                    quadraticTo(sx, sy, sx, sy + sRadius)
                    quadraticTo(sx, sy, sx - sRadius, sy)
                    quadraticTo(sx, sy, sx, sy - sRadius)
                }
                drawPath(p, color = color.copy(alpha = 0.75f))
            }
            drawMiniStar(width * 0.22f, height * 0.22f)
            drawMiniStar(width * 0.78f, height * 0.22f)
        }
    }
}

/**
 * An overlapping avatar stack for Jam rooms or group members.
 */
@Composable
internal fun AvatarGroup(
    usernames: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    overlap: Dp = 8.dp,
    maxDisplay: Int = 5
) {
    val display = usernames.take(maxDisplay)
    val remaining = usernames.size - display.size

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(-overlap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        display.forEach { username ->
            CuteAvatar(
                seed = username,
                size = size,
                showBorder = true,
                borderColor = MaterialTheme.colorScheme.surface
            )
        }
        if (remaining > 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remaining",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
