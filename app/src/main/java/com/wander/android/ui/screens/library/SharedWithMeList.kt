package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.data.sources.agro.AgroSharedListing
import com.wander.android.data.sources.agro.PlaylistRole
import com.wander.android.ui.components.ConfirmRequest
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.groupedListItem

/** Which playlists the tab is showing. */
internal enum class PlaylistScope { YOURS, SHARED }

/** "Yours" and "Shared with me", over the list. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistScopeToggle(scope: PlaylistScope, onSelect: (PlaylistScope) -> Unit, modifier: Modifier = Modifier) {
    val labels = mapOf(
        PlaylistScope.YOURS to stringResource(R.string.library_playlists_yours),
        PlaylistScope.SHARED to stringResource(R.string.library_playlists_shared)
    )
    ButtonGroup(overflowIndicator = {}, modifier = modifier.fillMaxWidth()) {
        PlaylistScope.entries.forEach { option ->
            toggleableItem(
                checked = scope == option,
                label = labels.getValue(option),
                onCheckedChange = { onSelect(option) }
            )
        }
    }
}

/**
 * Friends' playlists this account can open, each saying whose it is and what this account may do
 * there. Tapping one opens it, adding it to the library first when it is not there yet.
 */
internal fun LazyListScope.sharedWithMeItems(
    state: SharedWithMe,
    error: String?,
    onOpen: (AgroSharedListing) -> Unit,
    onRemove: (AgroSharedListing) -> Unit,
    onRetry: () -> Unit
) {
    error?.let { item(key = "shared_error") { SharedMessage(it) } }
    when (state) {
        SharedWithMe.NotLoaded, SharedWithMe.Loading -> items(count = 3, key = { "shared_skeleton_$it" }) {
            SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
        }

        is SharedWithMe.Failed -> item(key = "shared_failed") {
            SharedMessage(stringResource(R.string.shared_with_me_failed, state.message)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.common_try_again)) }
            }
        }

        is SharedWithMe.Loaded -> if (state.items.isEmpty()) {
            item(key = "shared_empty") { SharedMessage(stringResource(R.string.shared_with_me_empty)) }
        } else {
            itemsIndexed(state.items, key = { _, it -> "shared_${it.id}" }, contentType = { _, _ -> "playlist" }) { index, listing ->
                PlaylistRow(
                    playlist = listing.asPlaylist(),
                    index = index,
                    subtitle = subtitleOf(listing),
                    onClick = { onOpen(listing) },
                    // Long press to leave a blend, end one of your own, or unfollow.
                    onLongPress = { onRemove(listing) }.takeIf { listing.isRemovable },
                    modifier = Modifier.animateItem().groupedListItem(index, state.items.size)
                )
            }
        }
    }
}

@Composable
private fun SharedMessage(text: String, action: @Composable () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action()
    }
}

@Composable
private fun subtitleOf(listing: AgroSharedListing): String = listOfNotNull(
    stringResource(R.string.shared_with_me_by, listing.owner),
    if (listing.isBlend) stringResource(R.string.shared_with_me_blend) else stringResource(
        when (listing.role) {
            PlaylistRole.OWNER, PlaylistRole.EDITOR -> R.string.shared_playlist_access_editor
            PlaylistRole.CONTRIBUTOR -> R.string.shared_playlist_access_contributor
            PlaylistRole.VIEWER, PlaylistRole.NONE -> R.string.shared_playlist_access_viewer
        }
    ),
    listing.itemCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.shared_with_me_tracks, it, it) },
    stringResource(R.string.shared_with_me_in_library).takeIf { listing.isFollowing }
).joinToString(" · ")

private fun AgroSharedListing.asPlaylist() = UnifiedPlaylist(
    id = "shared:$id",
    source = SourceType.AGRO,
    name = title,
    songCount = itemCount
)

/** What taking [listing] away means, said before it is done: ending a blend is for everyone. */
@Composable
internal fun removalRequest(listing: AgroSharedListing, onConfirm: () -> Unit) = when {
    listing.isBlend && listing.isMine -> ConfirmRequest(
        title = stringResource(R.string.blend_end_confirm_title),
        message = stringResource(R.string.blend_end_confirm_message),
        confirmLabel = stringResource(R.string.blend_banner_end),
        onConfirm = onConfirm
    )
    listing.isBlend -> ConfirmRequest(
        title = stringResource(R.string.blend_leave_confirm_title),
        message = stringResource(R.string.blend_leave_confirm_message),
        confirmLabel = stringResource(R.string.blend_banner_leave),
        onConfirm = onConfirm
    )
    else -> ConfirmRequest(
        title = stringResource(R.string.shared_with_me_unfollow_title, listing.title),
        message = stringResource(R.string.shared_with_me_unfollow_message),
        confirmLabel = stringResource(R.string.shared_playlist_unfollow),
        onConfirm = onConfirm
    )
}
