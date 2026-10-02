package com.wander.android.ui.screens.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.PlaylistVisibility

private class VisibilityChoice(
    val visibility: PlaylistVisibility,
    @StringRes val title: Int,
    @StringRes val description: Int
)

private val CHOICES = listOf(
    VisibilityChoice(PlaylistVisibility.FRIENDS, R.string.playlist_visibility_friends, R.string.playlist_visibility_friends_desc),
    VisibilityChoice(PlaylistVisibility.PUBLIC, R.string.playlist_visibility_public, R.string.playlist_visibility_public_desc),
    VisibilityChoice(PlaylistVisibility.PRIVATE, R.string.playlist_visibility_private, R.string.playlist_visibility_private_desc)
)

/** Asks who can open a playlist about to be published to the paired Agro server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistVisibilitySheet(onPick: (PlaylistVisibility) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.playlist_share_visibility_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            CHOICES.forEach { choice ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(choice.visibility) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(choice.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(choice.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
