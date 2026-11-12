package com.wander.android.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.wander.android.R
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.theme.LocalReducedMotion

/**
 * Play/pause on a 9-sided cookie that turns slowly while music plays and stops where it is on
 * pause: the one shaped accent the player, the queue drawer and the mini player share.
 *
 * The turn is a `graphicsLayer` rotation read in the draw phase, so it never recomposes anything.
 * It still redraws every frame while it runs, which is why [spins] exists: a surface that stays on
 * screen for a whole listening session (the mini player) passes false and only gets a quarter turn
 * when the play state flips. Reduced motion stops the turn everywhere.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TurningCookiePlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onToggle: () -> Unit,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    spins: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val reducedMotion = LocalReducedMotion.current
    val rotation = remember { Animatable(0f) }
    val settle = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(isPlaying, spins, reducedMotion) {
        when {
            reducedMotion -> Unit
            isPlaying && spins -> while (true) {
                // One turn at a time from wherever it stopped, so a pause never snaps it back, and
                // folded back into 0..360 each lap so the angle never grows without bound.
                rotation.snapTo(rotation.value % FULL_TURN)
                rotation.animateTo(
                    targetValue = rotation.value + FULL_TURN,
                    animationSpec = tween(TURN_MILLIS, easing = LinearEasing)
                )
            }
            else -> {
                rotation.snapTo(rotation.value % FULL_TURN)
                rotation.animateTo(rotation.value + QUARTER_TURN, settle)
            }
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    val label = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play)
    val shape = MaterialShapes.Cookie9Sided.toShape()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .toggleable(
                value = isPlaying,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = size / 2),
                role = Role.Button,
                onValueChange = { onToggle() }
            )
            .semantics { contentDescription = label }
    ) {
        // Only the container turns; the icon on top of it stays upright.
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotation.value }
                .background(containerColor, shape)
        )
        // The icon names itself; the button already says the same, so the icon is hidden from
        // accessibility rather than read twice.
        Box(Modifier.clearAndSetSemantics { }) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                PlayPauseIcon(isPlaying = isPlaying, isBuffering = isBuffering, iconSize = iconSize)
            }
        }
    }
}

private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f

/** One turn every 14 seconds: slow enough to read as calm rather than as a loading spinner. */
private const val TURN_MILLIS = 14_000
