package com.wander.android.ui.screens.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.playback.EpisodeJumps
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.RepeatMode
import com.wander.android.ui.components.player.TurningCookiePlayButton
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPressScale

/**
 * The one transport row: shuffle · previous · play/pause · next · repeat — or, for a podcast
 * episode, back 10s and forward 30s in place of the skips, since missing a sentence is far more
 * common than wanting another episode. Holding either skip scrubs instead, until the finger lifts.
 *
 * Play/pause is the turning cookie the mini player and the queue share, as large as the row allows:
 * the skips are bare glyphs and shuffle/repeat are small toggles, so the eye lands on it first.
 * Paused, it settles a little smaller, the same "breathing" the cover does.
 */
@Composable
fun PlayerControls(
    state: PlaybackState,
    connection: PlayerConnection,
    modifier: Modifier = Modifier
) {
    val isEpisode = state.currentTrack?.isEpisode == true
    val prevInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    // Holding a skip scrubs through the track — see [rememberHoldToSeek]. Not for a live stream,
    // which has no end to scan towards, nor for anything the player says it cannot seek in.
    val canHoldSeek = state.isSeekable && state.currentTrack?.isLive != true
    val prevHold = rememberHoldToSeek(prevInteraction, connection, direction = -1, enabled = canHoldSeek)
    val nextHold = rememberHoldToSeek(nextInteraction, connection, direction = 1, enabled = canHoldSeek)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Whatever the four side controls leave, capped: on a narrow phone the cookie gives up a
        // few dp rather than pushing shuffle and repeat off the row.
        val playSize = (maxWidth - OrderToggleSize * 2 - SkipButtonSize * 2)
            .coerceIn(MinPlayButtonSize, MaxPlayButtonSize)

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ShuffleToggle(state, connection)
            SkipButton(
                onClick = {
                    when {
                        prevHold.consumeClick() -> Unit
                        isEpisode -> connection.seekBy(-EpisodeJumps.BACK_MS)
                        else -> connection.previous()
                    }
                },
                interaction = prevInteraction,
                icon = if (isEpisode) Icons.Rounded.Replay10 else Icons.Rounded.SkipPrevious,
                description = stringResource(if (isEpisode) R.string.player_rewind_10 else R.string.action_previous)
            )
            PlayPauseCookie(state, connection, playSize)
            SkipButton(
                onClick = {
                    when {
                        nextHold.consumeClick() -> Unit
                        isEpisode -> connection.seekBy(EpisodeJumps.FORWARD_MS)
                        else -> connection.next()
                    }
                },
                interaction = nextInteraction,
                icon = if (isEpisode) Icons.Rounded.Forward30 else Icons.Rounded.SkipNext,
                description = stringResource(if (isEpisode) R.string.player_forward_30 else R.string.action_next)
            )
            RepeatToggle(state, connection)
        }
    }
}

@Composable
private fun PlayPauseCookie(state: PlaybackState, connection: PlayerConnection, size: Dp) {
    val haptics = rememberHaptics()
    val scale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else PAUSED_PLAY_SCALE,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playButtonScale"
    )
    TurningCookiePlayButton(
        isPlaying = state.isPlaying,
        isBuffering = state.isBuffering,
        onToggle = {
            // Only play/pause among the transport controls. A skip announces itself — the song
            // changes — so a tick there repeats what the ears already got. Pausing into silence is
            // the one that benefits.
            haptics.toggled(!state.isPlaying)
            connection.togglePlayPause()
        },
        size = size,
        iconSize = PlayIconSize,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    )
}

@Composable
private fun ShuffleToggle(state: PlaybackState, connection: PlayerConnection) {
    PlayerOrderToggle(
        icon = Icons.Rounded.Shuffle,
        description = stringResource(R.string.action_shuffle),
        unavailableDescription = stringResource(R.string.player_shuffle_unavailable),
        active = state.isShuffle,
        enabled = !state.orderLocked,
        onToggle = connection::toggleShuffle,
        spinOnToggle = true
    )
}

@Composable
private fun RepeatToggle(state: PlaybackState, connection: PlayerConnection) {
    val one = state.repeatMode == RepeatMode.ONE
    PlayerOrderToggle(
        icon = if (one) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
        description = stringResource(if (one) R.string.action_repeat_one else R.string.action_repeat),
        unavailableDescription = stringResource(R.string.player_repeat_unavailable),
        active = state.repeatMode != RepeatMode.OFF,
        enabled = !state.orderLocked,
        // Cycles off → all → one, the order `PlayerConnection.toggleRepeat` keeps.
        onToggle = connection::toggleRepeat
    )
}

/** A skip: a bare glyph that swells slightly under the finger. */
@Composable
private fun SkipButton(
    onClick: () -> Unit,
    interaction: MutableInteractionSource,
    icon: ImageVector,
    description: String
) {
    val scale by rememberPressScale(interaction, pressedScale = SKIP_PRESSED_SCALE)
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .size(SkipButtonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(SkipIconSize))
    }
}

private val SkipButtonSize = 56.dp
private val SkipIconSize = 36.dp
private const val SKIP_PRESSED_SCALE = 1.12f

private val MaxPlayButtonSize = 112.dp
private val MinPlayButtonSize = 80.dp
private val PlayIconSize = 44.dp

/** A settle, not a shrink: the cookie stops turning and draws in a touch. */
private const val PAUSED_PLAY_SCALE = 0.9f
