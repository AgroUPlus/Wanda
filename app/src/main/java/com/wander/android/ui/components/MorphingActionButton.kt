package com.wander.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A small action — "Show all", "See all" — that never changes by cutting. Its corners morph: fully
 * round at rest, tightening under the finger, squarer while [selected] (the list it controls is
 * open). Its label and width move between states on a spring, and while [busy] the whole button
 * morphs into the expressive loading indicator and back.
 */
@Composable
fun MorphingActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    busy: Boolean = false
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = busy,
        transitionSpec = {
            (fadeIn(effects) + scaleIn(effects, initialScale = 0.6f)) togetherWith
                (fadeOut(effects) + scaleOut(effects, targetScale = 0.6f)) using
                SizeTransform(clip = false) { _, _ -> spatial }
        },
        contentAlignment = Alignment.CenterEnd,
        label = "morphingActionBusy",
        modifier = modifier
    ) { isBusy ->
        if (isBusy) {
            Box(modifier = Modifier.size(ButtonHeight), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(ButtonHeight))
            }
        } else {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val corner by animateDpAsState(
                targetValue = when {
                    pressed -> 8.dp
                    selected -> 12.dp
                    else -> ButtonHeight / 2
                },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "morphingActionCorner"
            )
            FilledTonalButton(
                onClick = onClick,
                shape = RoundedCornerShape(corner),
                interactionSource = interaction
            ) {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        fadeIn(effects) togetherWith fadeOut(effects) using
                            SizeTransform(clip = false) { _, _ -> spatial }
                    },
                    label = "morphingActionLabel"
                ) { Text(it) }
            }
        }
    }
}

private val ButtonHeight = 40.dp
