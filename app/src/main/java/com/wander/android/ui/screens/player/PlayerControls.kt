package com.wander.android.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.EpisodeJumps
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPlayPauseMorphShape
import com.wander.android.ui.components.player.PlayPauseIcon

/**
 * Transport controls: previous, play/pause, next — or, for a podcast episode, back 10s and
 * forward 30s. Moving between episodes is rare; missing a sentence or a sponsor read is not.
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
    val playButtonSize by animateDpAsState(
        targetValue = if (state.isPlaying) 92.dp else 88.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playButtonSize"
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playShape = rememberPlayPauseMorphShape(state.isPlaying, playPressed)

    val prevInteraction = remember { MutableInteractionSource() }
    val prevPressed by prevInteraction.collectIsPressedAsState()
    val nextInteraction = remember { MutableInteractionSource() }
    val nextPressed by nextInteraction.collectIsPressedAsState()
    val isEpisode = state.currentTrack?.isEpisode == true

    // An expressive connected group: each control shares the exact same height (`playButtonSize`)
    // and the two gaps between them never change. Playing, next and previous are rounded squares
    // and the play button takes the rest of the row as a wide rounded rectangle. Paused, the play
    // button draws in to a circle and the skips widen to take the space it gave up. When pressed,
    // the touched control swells while its neighbours give way.
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val available = maxWidth - ControlGap * 2
        val skipBase by animateDpAsState(
            targetValue = if (state.isPlaying) playButtonSize else (available - playButtonSize) / 2,
            animationSpec = spatial,
            label = "skipBase"
        )
        val prevDelta by animateDpAsState(
            targetValue = if (prevPressed) SkipPressGrowth else if (playPressed) -PlayPressSqueeze else 0.dp,
            animationSpec = spatial,
            label = "prevDelta"
        )
        val nextDelta by animateDpAsState(
            targetValue = if (nextPressed) SkipPressGrowth else if (playPressed) -PlayPressSqueeze else 0.dp,
            animationSpec = spatial,
            label = "nextDelta"
        )
        val prevWidth = skipBase + prevDelta
        val nextWidth = skipBase + nextDelta

        Row(
            horizontalArrangement = Arrangement.spacedBy(ControlGap),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SkipButton(
                onClick = if (isEpisode) {
                    { connection.seekBy(-EpisodeJumps.BACK_MS) }
                } else {
                    connection::previous
                },
                interaction = prevInteraction,
                pressed = prevPressed,
                icon = if (isEpisode) Icons.Rounded.Replay10 else Icons.Rounded.SkipPrevious,
                description = stringResource(if (isEpisode) R.string.player_rewind_10 else R.string.action_previous),
                width = prevWidth,
                height = playButtonSize
            )

            // Whatever the skips leave: the whole row's remainder while playing, a circle when paused.
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
                modifier = Modifier.size(
                    width = (available - prevWidth - nextWidth).coerceAtLeast(0.dp),
                    height = playButtonSize
                )
            ) {
                PlayPauseIcon(
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    iconSize = 48.dp
                )
            }

            SkipButton(
                onClick = if (isEpisode) {
                    { connection.seekBy(EpisodeJumps.FORWARD_MS) }
                } else {
                    connection::next
                },
                interaction = nextInteraction,
                pressed = nextPressed,
                icon = if (isEpisode) Icons.Rounded.Forward30 else Icons.Rounded.SkipNext,
                description = stringResource(if (isEpisode) R.string.player_forward_30 else R.string.action_next),
                width = nextWidth,
                height = playButtonSize
            )
        }
    }
}

/**
 * A skip control: a rounded square at rest, squaring off further under the finger —
 * the M3 Expressive press morph — while swelling into the row.
 */
@Composable
private fun SkipButton(
    onClick: () -> Unit,
    interaction: MutableInteractionSource,
    pressed: Boolean,
    icon: ImageVector,
    description: String,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val corner by animateDpAsState(
        targetValue = if (pressed) PressedCorner else height * SkipCornerFraction,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "skipCorner"
    )
    FilledTonalIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        interactionSource = interaction,
        modifier = modifier.size(width = width, height = height)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(SkipIconSize))
    }
}

private val SkipIconSize = 42.dp
private val PressedCorner = 16.dp

/** Matches the play toggle's playing corner, so the three read as one family. */
private const val SkipCornerFraction = 0.3f

private val SkipPressGrowth = 20.dp
private val PlayPressSqueeze = 10.dp

/** Tight enough that the three read as one connected group. */
private val ControlGap = 8.dp
