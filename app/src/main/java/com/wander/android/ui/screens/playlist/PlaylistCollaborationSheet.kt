package com.wander.android.ui.screens.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditOff
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistVisibility

private class CollaborationChoice(
    val access: EditAccess,
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val description: Int
)

private val CHOICES = listOf(
    CollaborationChoice(EditAccess.OFF, Icons.Rounded.EditOff, R.string.playlist_collab_off, R.string.playlist_collab_off_desc),
    CollaborationChoice(EditAccess.FRIENDS, Icons.Rounded.Group, R.string.playlist_collab_friends, R.string.playlist_collab_friends_desc),
    CollaborationChoice(EditAccess.PUBLIC, Icons.Rounded.GroupAdd, R.string.playlist_collab_public, R.string.playlist_collab_public_desc)
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
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            SheetHeading(stringResource(R.string.playlist_collab_title))
            CHOICES.forEach { choice ->
                val allowed = choice.access.allowedBy(visibility)
                SheetOption(
                    icon = choice.icon,
                    title = stringResource(choice.title),
                    description = stringResource(if (allowed) choice.description else R.string.playlist_collab_needs_wider),
                    onClick = { onPick(choice.access) },
                    selectable = true,
                    selected = choice.access == current,
                    enabled = allowed
                )
            }
        }
    }
}
