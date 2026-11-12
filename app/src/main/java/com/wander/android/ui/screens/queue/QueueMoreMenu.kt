package com.wander.android.ui.screens.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.ConfirmDialog
import com.wander.android.ui.components.newplaylist.NewPlaylistSheet
import com.wander.android.ui.components.newplaylist.plainSourceFor

/**
 * The round button on the drawer's cover, holding what the old header's icons did: save the queue
 * as a playlist, and clear it. Clearing still asks first — a long queue built by hand is gone in
 * one tap otherwise — and is not offered at all when [canClear] is false, which is while something
 * else (a jam, a listen-along) owns the order.
 *
 * Its disc is a fixed translucent black rather than a theme colour: it sits on the raw cover,
 * which can be any colour at all, and only a darkening reads on every one of them.
 */
@Composable
internal fun QueueMoreMenu(queue: List<UnifiedTrack>, canClear: Boolean, onClearQueue: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { open = true },
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.iconButtonColors(containerColor = DiscColor, contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.queue_next_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_save_as_playlist)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                onClick = {
                    open = false
                    naming = true
                }
            )
            if (canClear) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_clear_queue)) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                    onClick = {
                        open = false
                        confirming = true
                    }
                )
            }
        }
    }

    // The tracks are read when the sheet's Create is pressed, not when the item is, so a track
    // that finished in between is still in the playlist.
    if (naming) {
        NewPlaylistSheet(
            onCreated = { naming = false },
            onDismiss = { naming = false },
            tracks = queue,
            plainSource = plainSourceFor(queue),
            allowBlend = false
        )
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_clear_queue_title),
            message = pluralStringResource(R.plurals.confirm_clear_queue_message, queue.size, queue.size),
            confirmLabel = stringResource(R.string.action_clear_queue),
            onConfirm = onClearQueue,
            onDismiss = { confirming = false }
        )
    }
}

private val DiscColor = Color.Black.copy(alpha = 0.32f)
