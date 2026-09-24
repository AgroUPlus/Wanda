package com.wander.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

/**
 * Procedural cute avatar / identicon.
 *
 * When [avatarUrl] is provided, loads the remote image. Otherwise renders a deterministic,
 * cheerful character based on [seed].
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
    val cleanSeed = seed.trim()

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
        } else if (cleanSeed.isEmpty()) {
            PlaceholderAvatar(size = size)
        } else {
            ProceduralCanvasAvatar(seed = cleanSeed, size = size)
        }
    }
}

@Composable
private fun PlaceholderAvatar(size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
private fun ProceduralCanvasAvatar(
    seed: String,
    size: Dp
) {
    val hash = remember(seed) { seedHash(seed) }

    val paletteIndex = (hash % AVATAR_PALETTES.size).toInt().absoluteValue
    val palette = AVATAR_PALETTES[paletteIndex]

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
