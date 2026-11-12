package com.wander.android.ui.screens.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.TrackRow

/**
 * One track of the queue, on its own rounded tile in a group, Settings-style: tight corners where
 * it meets a neighbour, full ones where it does not, so each track reads as its own thing rather
 * than a line in one long list.
 *
 * [dragHandle] is the reorder grip, drawn trailing as Material lists do — the leading edge is
 * where the system back gesture lives. It is null for a track that cannot be moved: one already
 * played, or any track while the queue's order is locked. It is a grip, not a button: it answers a
 * drag and nothing else, so it carries no click for a screen reader to offer and then ignore.
 * Moving a track without dragging is [onMoveUp] and [onMoveDown], offered as accessibility actions
 * on the row and as entries in its long-press sheet.
 *
 * [onPlayAgain] is the trailing replay button of a played track in the History tab. The track
 * steps back from what is coming, dimmed; the button does not, so it still reads as something to
 * press.
 *
 * The tile is opaque because `SwipeToDismissBox` keeps the "Remove" backdrop stacked behind it at
 * full size the whole time; a translucent tile let the label show through every track.
 */
@Composable
internal fun QueueTrackTile(
    entry: QueueEntry,
    shape: RoundedCornerShape,
    isDragging: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    dragHandle: (Modifier.() -> Modifier)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onPlayAgain: (() -> Unit)? = null
) {
    val played = entry.role == QueueItemRole.PREVIOUS
    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    Surface(
        shape = shape,
        tonalElevation = if (isDragging) DraggingElevation else 0.dp,
        shadowElevation = if (isDragging) DraggingElevation else 0.dp,
        color = animateColorAsState(
            targetValue = if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "queueTileColor"
        ).value
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    customActions = listOfNotNull(
                        onMoveUp?.let { CustomAccessibilityAction(moveUp) { it(); true } },
                        onMoveDown?.let { CustomAccessibilityAction(moveDown) { it(); true } }
                    )
                }
        ) {
            TrackRow(
                track = entry.track,
                showBackground = false,
                onPlay = onPlay,
                onLongPress = onLongPress,
                modifier = Modifier.weight(1f).alpha(if (played) PLAYED_ALPHA else 1f)
            )
            onPlayAgain?.let { PlayAgainButton(entry.track.title, onClick = it) }
            dragHandle?.let { ReorderGrip(entry.track.title, handle = it) }
        }
    }
}

/** The History tab's trailing replay button, at full strength on a dimmed row. */
@Composable
private fun PlayAgainButton(title: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Replay,
            contentDescription = stringResource(R.string.queue_next_play_again, title),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** The trailing reorder grip: [handle] wires the drag, and it takes no click. */
@Composable
private fun ReorderGrip(title: String, handle: Modifier.() -> Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(HandleTouchSize)
            .handle()
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.queue_reorder, title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

private val DraggingElevation = 6.dp

/** The Material minimum touch target, which the grip used to get from being an `IconButton`. */
private val HandleTouchSize = 48.dp

/** Played tracks stay readable — they can be played again — but step back from what is coming. */
private const val PLAYED_ALPHA = 0.6f
