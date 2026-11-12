package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rememberPressScale

/**
 * Shuffle or repeat: a small toggle at either end of the transport row.
 *
 * Off, it is a bare glyph so the row reads as the three transport buttons first; on, a soft primary
 * disc lights behind it. In a jam or a listen-along the running order is somebody else's, so the
 * toggle dims rather than disappears — a control that vanishes reads as a bug, one that dims reads
 * as "not right now" — and announces [unavailableDescription] in place of [description].
 *
 * @param spinOnToggle for a toggle whose icon never swaps (shuffle): a half turn on each flip stands
 *   in for the "something happened" motion the others get from their icon changing.
 */
@Composable
internal fun PlayerOrderToggle(
    icon: ImageVector,
    description: String,
    unavailableDescription: String,
    active: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    spinOnToggle: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val colors = MaterialTheme.colorScheme

    FilledIconToggleButton(
        checked = active,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        shape = CircleShape,
        interactionSource = interaction,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurfaceVariant,
            checkedContainerColor = colors.primary.copy(alpha = CHECKED_CONTAINER_ALPHA),
            checkedContentColor = colors.primary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
        ),
        modifier = modifier
            .size(OrderToggleSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        val contentDescription = if (enabled) description else unavailableDescription
        if (spinOnToggle) {
            val rotation = remember { Animatable(0f) }
            LaunchedEffect(active) { rotation.animateTo(rotation.value + HALF_TURN, spatial) }
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(IconSize)
                    .graphicsLayer { rotationZ = rotation.value }
            )
        } else {
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f))
                        .togetherWith(fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f))
                },
                label = "order-toggle-icon"
            ) { targetIcon ->
                Icon(
                    imageVector = targetIcon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(IconSize)
                )
            }
        }
    }
}

private const val HALF_TURN = 180f

/** Faint enough that the disc reads as "lit", not as a fourth filled button competing with play. */
private const val CHECKED_CONTAINER_ALPHA = 0.18f

/** Material's disabled-content opacity, for an icon that is present but not yours to press. */
private const val DISABLED_ALPHA = 0.38f

internal val OrderToggleSize = 48.dp
private val IconSize = 24.dp
