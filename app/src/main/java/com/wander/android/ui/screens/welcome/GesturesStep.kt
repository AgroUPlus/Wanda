package com.wander.android.ui.screens.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/**
 * Teaches the player's gestures, one at a time, by showing each one being performed *and* its
 * result — the queue drawer, the speed/pitch popup, lyrics, the skip carousel, the mini player —
 * against a small mock of the real screen.
 *
 * Moves on its own, with nothing to tap: an earlier version let a chip row jump straight to a
 * gesture, but that gave someone a reason to stop watching the one demonstrated before it, which
 * defeats a step whose whole point is seeing every gesture play out. Reduced motion holds on the
 * first gesture instead of cycling, for the same reason [GesturePreview] stops looping there.
 */
@Composable
internal fun GesturesStep() {
    val gestures = remember { PlayerGesture.entries }
    var selected by remember { mutableStateOf(gestures.first()) }
    val reduced = LocalReducedMotion.current

    LaunchedEffect(selected, reduced) {
        if (reduced) return@LaunchedEffect
        delay(DwellMillis)
        selected = gestures[(gestures.indexOf(selected) + 1) % gestures.size]
    }

    Text(text = stringResource(R.string.welcome_gestures), style = MaterialTheme.typography.headlineLarge)
    Text(
        text = stringResource(R.string.welcome_player_mostly_gestures_fewer_buttons),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    GesturePreview(
        gesture = selected,
        modifier = Modifier.padding(top = 12.dp)
    )

    // Cross-faded rather than swapped: the caption changes on its own every few seconds, and a
    // hard cut at the foot of a looping animation reads as a glitch in the animation.
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            fadeIn(tween(CaptionFadeMillis)) togetherWith fadeOut(tween(CaptionFadeMillis))
        },
        label = "gestureCaption"
    ) { gesture ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(gesture.title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(gesture.detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Long enough to watch the loop twice before it moves on. */
private const val DwellMillis = 5_200L
private const val CaptionFadeMillis = 220
