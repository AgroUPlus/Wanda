package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.screens.library.blend.BlendInvites

/**
 * The leading row of the playlist list: Blend invitations, the yours/shared switch where sharing
 * is available, and New playlist beside Import.
 *
 * Invitations ride in this row rather than as rows of their own: the list's paging counts exactly
 * one item above the playlists.
 */
@Composable
internal fun PlaylistListActions(
    showsScope: Boolean,
    scope: PlaylistScope,
    onSelectScope: (PlaylistScope) -> Unit,
    canCreate: Boolean,
    onNewPlaylist: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    Column {
        BlendInvites(onJoined = onOpenPlaylist, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
        if (showsScope) {
            PlaylistScopeToggle(scope, onSelectScope, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (canCreate) {
                FilledTonalButton(
                    onClick = onNewPlaylist,
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.common_new_playlist),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            FilledTonalButton(
                onClick = onOpenImport,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                Text(text = stringResource(R.string.common_import), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
