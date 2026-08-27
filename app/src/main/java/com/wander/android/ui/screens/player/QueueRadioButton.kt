package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Matches the touch target an `IconButton` would have given this slot. */
private val ButtonSize = 48.dp

/** Enough to read as a shake; more than this and it reads as the icon being broken. */
private const val WiggleDegrees = 14f
private const val WiggleScale = 0.12f

/**
 * Queue button that doubles as the endless-radio toggle on long press.
 *
 * Radio used to be a labelled chip sitting next to this button. Folding it into a long press buys
 * back the space, but a long press is invisible and [isRadioMode] is *persisted* — so without a
 * visible state the user can be left in radio mode with nothing on screen admitting it. That is
 * what the tint is for, and why the state is spoken in the content description rather than left to
 * the label.
 *
 * The icon also gives a short wiggle each time the mode changes. A colour that eases from grey to
 * primary over a few hundred milliseconds is easy to miss on a screen dominated by album art —
 * movement is not, and it draws the eye to the thing that just changed rather than announcing it
 * somewhere else.
 */
@Composable
internal fun QueueRadioButton(
    isRadioMode: Boolean,
    onOpenQueue: () -> Unit,
    onToggleRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (isRadioMode) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "queueRadioTint"
    )

    // Skipped on the first composition: opening the player in radio mode is not a change, and a
    // control that wiggles every time the screen appears is noise rather than feedback.
    var settled by remember { mutableStateOf(false) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(isRadioMode) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        wiggle.snapTo(0f)
        // Target 0f, not 1f. `Animatable.animateTo` settles on its *target* once the spec has run,
        // whatever the last keyframe says — targeting 1f left the icon parked at a permanent 14
        // degrees and 1.12x the moment the wiggle finished.
        wiggle.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 420
                0f at 0
                -1f at 70
                1f at 180
                -0.5f at 290
                0f at 420
            }
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ButtonSize)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onOpenQueue,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleRadio()
                }
            )
            .semantics {
                contentDescription = if (isRadioMode) {
                    "Open queue. Radio on — long press to turn off"
                } else {
                    "Open queue. Long press to turn on radio"
                }
            }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                rotationZ = wiggle.value * WiggleDegrees
                val lift = 1f + kotlin.math.abs(wiggle.value) * WiggleScale
                scaleX = lift
                scaleY = lift
            }
        )
    }
}
