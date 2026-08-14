package com.wander.android.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wander.android.core.playback.PlaybackState
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.playback.RepeatMode

/**
 * Transport controls and endless radio toggle with clear visual feedback.
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ToggleButton(
                icon = Icons.Rounded.Shuffle,
                description = "Shuffle",
                active = state.isShuffle,
                onClick = connection::toggleShuffle
            )

            IconButton(onClick = connection::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(32.dp))
            }

            FilledIconButton(
                onClick = connection::togglePlayPause,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(playButtonSize)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(38.dp)
                )
            }

            IconButton(onClick = connection::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", modifier = Modifier.size(32.dp))
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

        RadioChip(
            isRadioMode = state.isRadioMode,
            onToggle = connection::toggleRadio,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun RadioChip(
    isRadioMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isRadioMode) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        label = "radioChipContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRadioMode) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "radioChipContent"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (isRadioMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onToggle)
            .semantics {
                // The label is just the feature name, so the state has to be spoken here.
                contentDescription = if (isRadioMode) "Radio on" else "Radio off"
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            // The chip's own filled/outlined state says whether it is on; the label only has to
            // name the feature, and it names it the same way the Home shelf does.
            Text(
                text = "Radio",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
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
