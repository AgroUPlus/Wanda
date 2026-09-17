package com.wander.android.ui.screens.welcome

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
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
 * Teaches the player's gestures, one at a time, by showing each one being performed.
 *
 * Gestures are the part of this app with nothing on screen to discover them by — that is the point
 * of a gesture, and it is also why the two most useful ones went unfound. A list of sentences would
 * be the cheap version; what makes a drag legible is watching where it starts and where it ends, so
 * each one is demonstrated on a mock of the screen it belongs to.
 *
 * Advances on its own so the whole vocabulary is seen without anyone having to tap five times, and
 * the chips stay tappable so a gesture can be gone back to. Auto-advance stops for good the moment
 * one is tapped: it exists to show what is there, not to pull the screen away from someone reading.
 */
@Composable
internal fun GesturesStep() {
    val gestures = remember { PlayerGesture.entries }
    var selected by remember { mutableStateOf(gestures.first()) }
    var autoAdvance by remember { mutableStateOf(true) }
    val reduced = LocalReducedMotion.current

    // Reduced motion gets no carousel either: the demonstration is already still, and a step that
    // changed under the reader would be the same problem in a slower form.
    LaunchedEffect(selected, autoAdvance, reduced) {
        if (!autoAdvance || reduced) return@LaunchedEffect
        delay(DwellMillis)
        selected = gestures[(gestures.indexOf(selected) + 1) % gestures.size]
    }

    Text(text = stringResource(R.string.welcome_gestures), style = MaterialTheme.typography.headlineLarge)
    Text(
        text = stringResource(R.string.welcome_player_mostly_gestures_fewer_buttons) +
            "These are all of them.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        gestures.forEach { gesture ->
            FilterChip(
                selected = gesture == selected,
                onClick = {
                    selected = gesture
                    autoAdvance = false
                },
                label = { Text(gesture.chipLabel) }
            )
        }
    }

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

/** Two or three words, so five of them fit a phone's width without scrolling on most. */
private val PlayerGesture.chipLabel: String
    get() = when (this) {
        PlayerGesture.OPEN_QUEUE -> "Queue"
        PlayerGesture.SPEED_PITCH -> "Speed"
        PlayerGesture.LYRICS -> "Lyrics"
        PlayerGesture.SKIP -> "Skip"
        PlayerGesture.COLLAPSE -> "Minimise"
    }

/** Long enough to watch the loop twice before it moves on. */
private const val DwellMillis = 5_200L
private const val CaptionFadeMillis = 220
