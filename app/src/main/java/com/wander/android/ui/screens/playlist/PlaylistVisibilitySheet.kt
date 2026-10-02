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

@StringRes
internal fun visibilityTitle(visibility: PlaylistVisibility): Int =
    CHOICES.first { it.visibility == visibility }.title

/**
 * Asks who can open a playlist on the paired Agro server: before it is published, or afterwards to
 * change it, with [current] marked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistVisibilitySheet(
    current: PlaylistVisibility?,
    onPick: (PlaylistVisibility) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.playlist_share_visibility_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            CHOICES.forEach { choice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(choice.visibility) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(choice.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(choice.description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (choice.visibility == current) {
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
