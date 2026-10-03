package com.wander.android.ui.screens.playlist

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.repository.sharedplaylist.SharedPlaylistState
import com.wander.android.data.repository.sharedplaylist.SharedSyncState
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistRole

/**
 * Where a shared playlist stands, always above its tracks, so a copy that is behind is never
 * mistaken for a current one: synced, saving, out of sync (with a retry), or no longer shared.
 * Says too what this account may do with it, and offers to unfollow one it does not own.
 */
@Composable
internal fun SharedPlaylistBanner(
    state: SharedPlaylistState,
    onRetry: () -> Unit,
    onUnfollow: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val status = statusOf(state.syncState)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (status.warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (status.warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            AnimatedContent(targetState = status.icon, label = "sync-icon") { icon ->
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                text = stringResource(R.string.shared_playlist_banner, stringResource(status.label), stringResource(accessLabel(state))),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp)
            )
            if (state.syncState == SharedSyncState.OUT_OF_SYNC) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.shared_playlist_retry)) }
            }
            onUnfollow?.let { unfollow ->
                TextButton(onClick = unfollow) { Text(stringResource(R.string.shared_playlist_unfollow)) }
            }
        }
    }
}

private class Status(val icon: ImageVector, val label: Int, val warn: Boolean = false)

private fun statusOf(state: SharedSyncState) = when (state) {
    SharedSyncState.SYNCED -> Status(Icons.Rounded.CloudDone, R.string.shared_playlist_synced)
    SharedSyncState.PENDING -> Status(Icons.Rounded.CloudSync, R.string.shared_playlist_saving)
    SharedSyncState.SYNCING -> Status(Icons.Rounded.CloudSync, R.string.shared_playlist_syncing)
    SharedSyncState.OUT_OF_SYNC -> Status(Icons.Rounded.CloudOff, R.string.shared_playlist_out_of_sync, warn = true)
    SharedSyncState.REVOKED -> Status(Icons.Rounded.LinkOff, R.string.shared_playlist_no_longer_shared, warn = true)
}

/** What this account may do, in a few words. */
private fun accessLabel(state: SharedPlaylistState): Int = when (state.effectiveRole) {
    PlaylistRole.OWNER -> when (state.editAccess) {
        EditAccess.OFF -> R.string.shared_playlist_access_owner
        EditAccess.FRIENDS -> R.string.shared_playlist_access_owner_friends
        EditAccess.PUBLIC -> R.string.shared_playlist_access_owner_public
    }
    PlaylistRole.EDITOR -> R.string.shared_playlist_access_editor
    PlaylistRole.CONTRIBUTOR -> R.string.shared_playlist_access_contributor
    PlaylistRole.VIEWER, PlaylistRole.NONE -> R.string.shared_playlist_access_viewer
}
