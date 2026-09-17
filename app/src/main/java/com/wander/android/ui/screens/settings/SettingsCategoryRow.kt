package com.wander.android.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialShapes
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPressMorphShape
import com.wander.android.ui.components.rememberPressScale

/**
 * One category on the settings hub: a coloured badge, a title, and a sentence saying what is inside.
 *
 * The badge is the category's [SettingsCategory.hue] at low alpha with the glyph at full strength
 * over it. One colour, two opacities — so the pairing can never be illegible the way two
 * independently chosen colours can, and it needs no light/dark variant.
 *
 * Two motions, and they do different jobs. The row springs down under a finger like every card in
 * the app ([rememberPressScale]), which is the press. The glyph then gives a short wiggle on
 * release, which is the *acknowledgement* — the same idiom as the player's queue button. Both specs
 * come from `MaterialTheme.motionScheme`; nothing here hand-rolls a spring.
 */
@Composable
internal fun SettingsCategoryRow(
    category: SettingsCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource, label = "settingsCategoryPress")
    val haptics = rememberHaptics()

    // A counter rather than a boolean: two taps in a row have to replay the wiggle, and a flag
    // already true the second time would not.
    var wiggles by remember { mutableIntStateOf(0) }
    val wiggle = remember { Animatable(0f) }
    val wiggleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(wiggles) {
        if (wiggles == 0) return@LaunchedEffect
        wiggle.animateTo(1f, wiggleSpec)
        wiggle.animateTo(0f, wiggleSpec)
    }

    val isPressed by interactionSource.collectIsPressedAsState()
    val restingPolygon = remember(category) {
        when (category) {
            SettingsCategory.CONNECTIONS -> MaterialShapes.Clover4Leaf
            SettingsCategory.SYNC -> MaterialShapes.Burst
            SettingsCategory.APPEARANCE -> MaterialShapes.Cookie12Sided
            SettingsCategory.PLAYBACK -> MaterialShapes.Square
            SettingsCategory.EXTERNAL -> MaterialShapes.Cookie4Sided
            SettingsCategory.PRIVACY -> MaterialShapes.Pentagon
            SettingsCategory.ABOUT -> MaterialShapes.Circle
        }
    }
    val badgeShape = rememberPressMorphShape(
        resting = restingPolygon,
        pressed = MaterialShapes.Circle,
        isPressed = isPressed
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.settled()
                    wiggles++
                    onClick()
                }
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(BadgeSize)
                .background(category.hue.copy(alpha = BadgeAlpha), badgeShape)
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.hue,
                modifier = Modifier
                    .size(GlyphSize)
                    .graphicsLayer {
                        rotationZ = wiggle.value * WiggleDegrees
                        val s = 1f + wiggle.value * WiggleScale
                        scaleX = s
                        scaleY = s
                    }
            )
        }

        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(category.label),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(category.subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private val BadgeSize = 48.dp
private val GlyphSize = 26.dp

/**
 * Enough tint to read as a coloured badge, little enough that the glyph over it stays the thing you
 * see. The same value in both themes: it is an alpha, so it lightens a dark surface and darkens a
 * light one by exactly the amount that keeps the contrast.
 */
private const val BadgeAlpha = 0.16f

private const val WiggleDegrees = 12f
private const val WiggleScale = 0.10f
