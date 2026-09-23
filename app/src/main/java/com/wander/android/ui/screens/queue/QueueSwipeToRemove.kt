package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.rememberHaptics
import kotlinx.coroutines.flow.drop

/**
 * Swipe a queue row away, either direction, on Material's own [SwipeToDismissBox].
 *
 * The platform component rather than a hand-rolled drag because it decides the way people expect:
 * a flick faster than 125 dp/s removes the row however short it was, and a slow drag commits once
 * it passes a fixed 56 dp — a distance, not a fraction of the row, so it feels the same on every
 * screen. It also settles, animates out and exposes the dismiss actions to accessibility itself.
 *
 * Both directions, because a rightward swipe begins near the left edge, where Android's back
 * gesture lives — right-only removal kept pulling the drawer down instead of taking the row.
 */
@Composable
internal fun QueueSwipeToRemove(
    enabled: Boolean,
    shape: Shape,
    onRemove: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState()

    // One tick as the swipe crosses into "letting go removes this", and again if it is pulled
    // back out — so the release carries no surprise.
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.drop(1).collect { haptics.tick() }
    }

    SwipeToDismissBox(
        state = state,
        gesturesEnabled = enabled,
        onDismiss = { onRemove() },
        backgroundContent = {
            val direction = state.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                RemoveBackdrop(fromStart = direction == SwipeToDismissBoxValue.StartToEnd, progress = state.progress)
            }
        },
        // Clipped to the row's own frame so a swipe reveals "Remove" inside that frame rather
        // than as a full-width strip across the group.
        modifier = Modifier.clip(shape),
        content = content
    )
}

/**
 * What the swipe reveals underneath: the consequence, named, in the colour of consequences — on
 * the side the row is uncovering, so it is visible whichever way the row is pulled.
 */
@Composable
private fun RemoveBackdrop(fromStart: Boolean, progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(if (fromStart) Alignment.CenterStart else Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.graphicsLayer {
                    val scale = 0.6f + 0.4f * progress.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                }
            )
            Text(
                text = stringResource(R.string.common_remove),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}
