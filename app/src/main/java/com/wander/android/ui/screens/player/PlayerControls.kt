package com.wander.android.ui.screens.player

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
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
    val playButtonSize by animateDpAsState(
        targetValue = if (state.isPlaying) 76.dp else 72.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playButtonSize"
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
            onClick = connection::toggleShuffle
        )

        IconButton(onClick = connection::previous, modifier = Modifier.size(56.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Previous track",
                modifier = Modifier.size(32.dp)
            )
        }

        FilledIconButton(
            onClick = connection::togglePlayPause,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(playButtonSize)
        ) {
            PlayPauseIcon(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                iconSize = 38.dp
            )
        }

        IconButton(onClick = connection::next, modifier = Modifier.size(56.dp)) {
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
            onClick = connection::toggleRepeat
        )
    }
}

@Composable
private fun ToggleButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
