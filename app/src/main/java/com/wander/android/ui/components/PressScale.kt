package com.wander.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

/**
 * The scale a pressable card should draw itself at, springing from the expressive motion scheme.
 *
 * Shared so every card on Home answers a press identically, and so the spring spec stays a theme
 * lookup rather than a hand-rolled value repeated once per card.
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
    label: String = "pressScale"
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = label
    )
}
