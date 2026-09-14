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
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPlayPauseMorphShape
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
    // Sized against the space that actually exists. The row lives inside a container padded 24dp
    // each side, so on a 411dp phone these five buttons share 363dp — and `SpaceEvenly` puts a gap
    // at each end as well as between, six in all. At these sizes they total 328dp, leaving about
    // 6dp a gap. Another 30% on top would be 391dp of button in 363dp of row, which does not
    // overflow gracefully; it clips.
    val playButtonSize by animateDpAsState(
        targetValue = if (state.isPlaying) 84.dp else 80.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playButtonSize"
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playShape = rememberPlayPauseMorphShape(state.isPlaying, playPressed)

    val prevInteraction = remember { MutableInteractionSource() }
    val prevPressed by prevInteraction.collectIsPressedAsState()
    val prevShape = rememberPressMorphShape(
        resting = MaterialShapes.Square,
        pressed = MaterialShapes.Circle,
        isPressed = prevPressed
    )

    val nextInteraction = remember { MutableInteractionSource() }
    val nextPressed by nextInteraction.collectIsPressedAsState()
    val nextShape = rememberPressMorphShape(
        resting = MaterialShapes.Square,
        pressed = MaterialShapes.Circle,
        isPressed = nextPressed
    )

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

        FilledTonalIconButton(
            onClick = connection::previous,
            shape = prevShape,
            interactionSource = prevInteraction,
            modifier = Modifier.size(66.dp)
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                modifier = Modifier.size(35.dp)
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
                iconSize = 42.dp
            )
        }

        FilledTonalIconButton(
            onClick = connection::next,
            shape = nextShape,
            interactionSource = nextInteraction,
            modifier = Modifier.size(66.dp)
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next track",
                modifier = Modifier.size(35.dp)
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
    FilledIconToggleButton(
        checked = active,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interaction,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
        ),
        modifier = Modifier
            // 48dp is the floor, not a preference: Material's minimum touch target, and these
            // sat under it at 44. Shuffle and repeat are the two controls people miss.
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (enabled) description else disabledDescription ?: description,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** Material's disabled-content opacity, for an icon that is present but not yours to press. */
private const val DisabledAlpha = 0.38f
