package com.wander.android.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPlayPauseMorphShape
import com.wander.android.ui.components.rememberPressMorphShape
import com.wander.android.ui.components.player.PlayPauseIcon

/**
 * Transport controls: previous, play/pause, next.
 *
 * Shuffle and repeat used to flank these. They are secondary — you set them once and forget them,
 * while these three are pressed constantly — and they now sit in [PlayerActionBar] below, which is
 * also where the like button and the overflow menu ended up. See the note there.
 *
 * The radio toggle is deliberately not here either: it lives on a long press of the queue button
 * in the top action bar — see `QueueRadioButton`.
 */
@Composable
fun PlayerControls(
    state: PlaybackState,
    connection: PlayerConnection,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    // Sized against the space that actually exists. With shuffle and repeat moved out to
    // [PlayerActionBar], three buttons share the 363dp this row gets on a 411dp phone instead of
    // five — so the play button takes the room the other two were using rather than the row
    // sitting in the middle of a gap.
    val playButtonSize by animateDpAsState(
        targetValue = if (state.isPlaying) 100.dp else 96.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playButtonSize"
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playShape = rememberPlayPauseMorphShape(state.isPlaying, playPressed)

    val prevInteraction = remember { MutableInteractionSource() }
    val prevPressed by prevInteraction.collectIsPressedAsState()
    // Gem, against the play button's own rounded morph. The three are deliberately *unalike*: a
    // cookie either side of a cookie made the row read as three of the same thing, and the one
    // control you aim for without looking should be the one shaped differently from its neighbours.
    val prevShape = rememberPressMorphShape(
        resting = MaterialShapes.Gem,
        pressed = MaterialShapes.Circle,
        isPressed = prevPressed
    )

    val nextInteraction = remember { MutableInteractionSource() }
    val nextPressed by nextInteraction.collectIsPressedAsState()
    val nextShape = rememberPressMorphShape(
        resting = MaterialShapes.Gem,
        pressed = MaterialShapes.Circle,
        isPressed = nextPressed
    )

    Row(
        // Centred with a fixed gap, not `SpaceEvenly`. Even spacing puts a gap at each end of the
        // row as well as between the buttons, so making the buttons bigger widened the outer gaps
        // instead of closing the inner ones — the three controls drifted apart as they grew.
        horizontalArrangement = Arrangement.spacedBy(ControlGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        FilledTonalIconButton(
            onClick = connection::previous,
            shape = prevShape,
            interactionSource = prevInteraction,
            modifier = Modifier.size(SkipButtonSize)
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                modifier = Modifier.size(SkipIconSize)
            )
        }

        FilledIconButton(
            onClick = {
                // Only play/pause among the transport controls. Skip and previous announce
                // themselves — the song changes — so a tick there is repeating what the ears
                // already got. Pausing into silence is the one that benefits.
                haptics.toggled(!state.isPlaying)
                connection.togglePlayPause()
            },
            shape = playShape,
            interactionSource = playInteraction,
            modifier = Modifier.size(playButtonSize)
        ) {
            PlayPauseIcon(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                iconSize = 48.dp
            )
        }

        FilledTonalIconButton(
            onClick = connection::next,
            shape = nextShape,
            interactionSource = nextInteraction,
            modifier = Modifier.size(SkipButtonSize)
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                modifier = Modifier.size(SkipIconSize)
            )
        }

    }
}

/**
 * Fatter than they were, and closer in.
 *
 * The skip buttons carry the same weight as the play button in use and used to be barely half its
 * size with a wide gap either side, which read as one control with two afterthoughts.
 */
private val SkipButtonSize = 84.dp
private val SkipIconSize = 42.dp

/** Tight enough that the three read as one cluster. */
private val ControlGap = 10.dp
