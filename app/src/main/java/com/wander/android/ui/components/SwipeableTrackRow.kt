package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.FingerprintStatus

/**
 * [TrackRow] with a swipe action either side, for the lists where "play next" and "queue" are
 * common enough to earn a gesture — Search results and library shelves, chiefly. The queue's own
 * rows keep the plain [TrackRow] plus [androidx.compose.material3.SwipeToDismissBox]-for-removal
 * from `QueueUpNext`, which this mirrors rather than replaces: neither swipe here ever removes the
 * row, so [SwipeToDismissBoxValue.Settled] is the only state the box is ever left in.
 *
 * Swipe right reveals "Play Next"; swipe left reveals the like/queue action already offered by
 * [onToggleLike] — swiping is a shortcut for the same tap, not a different feature.
 */
@Composable
fun SwipeableTrackRow(
    track: UnifiedTrack,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    enabled: Boolean = track.isPlayableNow(),
    fingerprintStatus: FingerprintStatus? = null
) {
    val haptics = rememberHaptics()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.confirmed()
                    onPlayNext()
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onToggleLike?.let {
                        haptics.toggled(!track.isLiked)
                        it()
                    }
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Never actually dismissed: both directions are shortcuts for an action the row
            // already offers by other means, not a way to remove it from the list.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled && onToggleLike != null,
        backgroundContent = { SwipeActionBackdrop(dismissState.dismissDirection, track.isLiked) }
    ) {
        TrackRow(
            track = track,
            onPlay = onPlay,
            modifier = modifier,
            isPlaying = isPlaying,
            onToggleLike = onToggleLike,
            onLongPress = onLongPress,
            enabled = enabled,
            fingerprintStatus = fingerprintStatus
        )
    }
}

/** What each direction reveals underneath — mirrors `QueueUpNext`'s `RemoveBackdrop`. */
@Composable
private fun SwipeActionBackdrop(direction: SwipeToDismissBoxValue, isLiked: Boolean) {
    if (direction == SwipeToDismissBoxValue.Settled) return

    val isPlayNext = direction == SwipeToDismissBoxValue.StartToEnd
    val color = if (isPlayNext) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val alignment = if (isPlayNext) Alignment.CenterStart else Alignment.CenterEnd
    val label = when {
        isPlayNext -> stringResource(R.string.swipe_action_play_next)
        isLiked -> stringResource(R.string.swipe_action_unlike)
        else -> stringResource(R.string.swipe_action_like)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(alignment)
        ) {
            Icon(
                imageVector = if (isPlayNext) Icons.Rounded.PlaylistAdd else Icons.Rounded.QueueMusic,
                contentDescription = null
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
