package com.wander.android.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * "Start radio" — a station out of nothing, in one press.
 *
 * Everything else on Home asks the user to choose something first: a shelf, a mix, a track. This
 * is for the times when choosing is itself the friction, which for a music player is most times.
 *
 * Bottom-left rather than the usual bottom-right: the right-hand side of the bottom edge is where
 * the thumb rests over the navigation bar and the mini player's controls, and a button that
 * starts playing something over the top of them is a button that gets pressed by accident.
 *
 * Icon only, and small. It started as an extended FAB with a label and it dominated the corner of
 * a screen whose whole job is showing artwork — the icon says it on its own, and a pill that size
 * competes with the content rather than sitting beside it.
 *
 * Hidden while the list is moving. A button pinned over a scrolling feed is in the way of the
 * thing being scrolled, and it is never what the hand is doing mid-flick.
 *
 * While the station is being assembled the icon pulses rather than swapping in a spinner — the
 * press has visibly done something, and the button keeps its size so nothing shifts underneath
 * the finger.
 */
@Composable
internal fun InstantRadioFab(
    isStarting: Boolean,
    isScrolling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "radio-fab")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "radio-pulse"
    )

    AnimatedVisibility(
        // Never yanked away mid-press: a start already in flight keeps its button, or the
        // pulsing feedback would vanish along with it.
        visible = !isScrolling || isStarting,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            // Its own shadow only. This cannot out-stack the docked player — that is drawn after
            // the whole nav host and always wins — so the two are kept from overlapping by the
            // caller's bottom offset rather than by elevation. See `HomeScreen`.
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = if (isStarting) "Starting radio" else "Start radio",
                modifier = Modifier.graphicsLayer { alpha = if (isStarting) pulse else 1f }
            )
        }
    }
}
