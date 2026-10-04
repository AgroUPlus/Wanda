package com.wander.android.ui.components.newplaylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.EditAccess
import com.wander.android.data.sources.agro.PlaylistVisibility
import com.wander.android.ui.components.ConnectedToggleGroup

/**
 * Who can open it and who can edit it. Editing never reaches further than opening: "anyone who
 * opens it" is only offered once everyone can, as the server would narrow it anyway.
 */
@Composable
internal fun CollaborativeOptions(state: NewPlaylistUiState, viewModel: NewPlaylistViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionLabel(R.string.new_playlist_who_can_open)
        ConnectedToggleGroup(
            options = listOf(PlaylistVisibility.FRIENDS, PlaylistVisibility.PUBLIC),
            selected = state.visibility,
            label = {
                stringResource(
                    if (it == PlaylistVisibility.PUBLIC) R.string.new_playlist_visibility_public
                    else R.string.new_playlist_visibility_friends
                )
            },
            onSelect = viewModel::setVisibility
        )

        if (state.visibility == PlaylistVisibility.PUBLIC) {
            OptionLabel(R.string.new_playlist_who_can_edit)
            ConnectedToggleGroup(
                options = listOf(EditAccess.FRIENDS, EditAccess.PUBLIC),
                selected = state.editAccess,
                label = {
                    stringResource(
                        if (it == EditAccess.PUBLIC) R.string.new_playlist_edit_public
                        else R.string.new_playlist_edit_friends
                    )
                },
                onSelect = viewModel::setEditAccess
            )
            if (state.editAccess == EditAccess.PUBLIC) {
                Text(
                    text = stringResource(R.string.new_playlist_edit_public_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A small heading over one choice in the sheet. */
@Composable
internal fun OptionLabel(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}
