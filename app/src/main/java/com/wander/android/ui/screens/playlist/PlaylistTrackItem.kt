package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberShelfEntranceScale
import sh.calvin.reorderable.ReorderableItem

/**
 * One playlist row in its grouped cell, with a trailing reorder grip when [draggable] — trailing,
 * as in the queue, because the leading edge is where the system back gesture lives. Move up and
 * Move down stay in the row's actions sheet for anyone who cannot drag.
 */
@Composable
internal fun LazyItemScope.PlaylistTrackItem(
    reorder: PlaylistReorder,
    entry: PlaylistEntry,
    position: Int,
    count: Int,
    draggable: Boolean,
    row: @Composable RowScope.(Modifier) -> Unit
) {
    val haptics = rememberHaptics()
    ReorderableItem(reorder.state, key = entry.key, enabled = draggable) { isDragging ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .scale(rememberShelfEntranceScale(position))
                .groupedListItem(position, count)
                .then(
                    if (isDragging) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    else Modifier
                )
        ) {
            row(Modifier.weight(1f))
            if (draggable) {
                IconButton(
                    onClick = {},
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .draggableHandle(
                            onDragStarted = {
                                haptics.heldDown()
                                reorder.onDragStarted(entry)
                            },
                            onDragStopped = {
                                haptics.settled()
                                reorder.onDragStopped()
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.playlist_reorder, entry.track.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
