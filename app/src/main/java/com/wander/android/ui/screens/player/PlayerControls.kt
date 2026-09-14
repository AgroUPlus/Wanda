package com.wander.android.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.PlayPressed
import com.wander.android.ui.components.PlayResting
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPressMorphShape
import com.wander.android.ui.components.rememberPressScale
import com.wander.android.ui.components.player.PlayPauseIcon
import com.wander.android.core.playback.RepeatMode

/**
 * Transport controls.
 *
 * The radio toggle is deliberately not here: it lives on a long press of the queue button in the
 * top action bar — see `QueueRadioButton`.
 */
@Composable
fun PlayerControls(
    state: PlaybackState,
    connection: PlayerConnection,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val playButtonSize by animateDpAsState(
        targetValue = if (state.isPlaying) 76.dp else 72.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playButtonSize"
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playShape = rememberPressMorphShape(PlayResting, PlayPressed, playPressed)

    val prevInteraction = remember { MutableInteractionSource() }
    val prevScale by rememberPressScale(prevInteraction)

    val nextInteraction = remember { MutableInteractionSource() }
    val nextScale by rememberPressScale(nextInteraction)

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        ToggleButton(
            icon = Icons.Rounded.Shuffle,
            description = "Shuffle",
            active = state.isShuffle,
            onClick = connection::toggleShuffle,
            // In a jam or a listen-along the running order is somebody else's. The button stays
            // in place rather than disappearing — a control that vanishes reads as a bug, one
            // that dims reads as "not right now".
            enabled = !state.orderLocked,
            disabledDescription = "Shuffle, unavailable while the room chooses the order"
        )

        IconButton(
            onClick = connection::previous,
            interactionSource = prevInteraction,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = prevScale; scaleY = prevScale }
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                modifier = Modifier.size(32.dp)
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
                iconSize = 38.dp
            )
        }

        IconButton(
            onClick = connection::next,
            interactionSource = nextInteraction,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { scaleX = nextScale; scaleY = nextScale }
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                modifier = Modifier.size(32.dp)
            )
        }

        ToggleButton(
            icon = when (state.repeatMode) {
                RepeatMode.ONE -> Icons.Rounded.RepeatOne
                else -> Icons.Rounded.Repeat
            },
            description = "Repeat",
            active = state.repeatMode != RepeatMode.OFF,
            onClick = connection::toggleRepeat,
            enabled = !state.orderLocked,
            disabledDescription = "Repeat, unavailable while the room chooses the order"
        )
    }
}

@Composable
private fun ToggleButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledDescription: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interaction)
    IconToggleButton(
        checked = active,
        onCheckedChange = { onClick() },
        enabled = enabled,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContentColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
        ),
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (enabled) description else disabledDescription ?: description
        )
    }
}

/** Material's disabled-content opacity, for an icon that is present but not yours to press. */
private const val DisabledAlpha = 0.38f
