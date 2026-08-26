package com.wander.android.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
 * While the station is being assembled the icon pulses instead of the label changing to a
 * spinner — the press has visibly done something, and the button keeps its size so the layout
 * does not jump underneath the finger.
 */
@Composable
internal fun InstantRadioFab(
    isStarting: Boolean,
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

    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { alpha = if (isStarting) pulse else 1f }
            )
            Spacer(Modifier.width(10.dp))
            Text(if (isStarting) "Tuning in…" else "Start radio")
        }
    }
}
