package com.wander.android.ui.screens.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistVisibility

private class CollaborationChoice(
    val access: EditAccess,
    @StringRes val title: Int,
    @StringRes val description: Int
)

private val CHOICES = listOf(
    CollaborationChoice(EditAccess.OFF, R.string.playlist_collab_off, R.string.playlist_collab_off_desc),
    CollaborationChoice(EditAccess.FRIENDS, R.string.playlist_collab_friends, R.string.playlist_collab_friends_desc),
    CollaborationChoice(EditAccess.PUBLIC, R.string.playlist_collab_public, R.string.playlist_collab_public_desc)
)

@StringRes
internal fun collaborationTitle(access: EditAccess): Int = CHOICES.first { it.access == access }.title

/**
 * Asks who besides the owner may change a shared playlist. A level wider than who can open the
 * playlist is shown but cannot be picked — the server would narrow it anyway — with the reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistCollaborationSheet(
    visibility: PlaylistVisibility,
    current: EditAccess,
    onPick: (EditAccess) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.playlist_collab_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            CHOICES.forEach { choice ->
                val allowed = choice.access.allowedBy(visibility)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = allowed) { onPick(choice.access) }
                        .alpha(if (allowed) 1f else DISABLED_ALPHA)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(choice.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(if (allowed) choice.description else R.string.playlist_collab_needs_wider),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (choice.access == current) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
