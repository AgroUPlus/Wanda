package com.wander.android.ui.screens.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.PlaylistVisibility

private class VisibilityChoice(
    val visibility: PlaylistVisibility,
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val description: Int
)

private val CHOICES = listOf(
    VisibilityChoice(PlaylistVisibility.FRIENDS, Icons.Rounded.Group, R.string.playlist_visibility_friends, R.string.playlist_visibility_friends_desc),
    VisibilityChoice(PlaylistVisibility.PUBLIC, Icons.Rounded.Public, R.string.playlist_visibility_public, R.string.playlist_visibility_public_desc),
    VisibilityChoice(PlaylistVisibility.PRIVATE, Icons.Rounded.Lock, R.string.playlist_visibility_private, R.string.playlist_visibility_private_desc)
)

@StringRes
internal fun visibilityTitle(visibility: PlaylistVisibility): Int =
    CHOICES.first { it.visibility == visibility }.title

/**
 * Asks who can open a playlist on the paired Agro server: before it is published, or afterwards to
 * change it, with [current] marked. Laid out like the share sheet it opens from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistVisibilitySheet(
    current: PlaylistVisibility?,
    onPick: (PlaylistVisibility) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            SheetHeading(stringResource(R.string.playlist_share_visibility_title))
            CHOICES.forEach { choice ->
                SheetOption(
                    icon = choice.icon,
                    title = stringResource(choice.title),
                    description = stringResource(choice.description),
                    onClick = { onPick(choice.visibility) },
                    selectable = true,
                    selected = choice.visibility == current
                )
            }
        }
    }
}
