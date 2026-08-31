package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.ui.components.AddToPlaylistController
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.NewPlaylistDialog
import com.wander.android.ui.components.PlaylistActionsSheet
import com.wander.android.ui.components.SkeletonRow
import com.wander.android.ui.components.listInset

private const val PLAYLISTS_PAGE_SIZE = 30
private const val PAGE_PREFETCH_DISTANCE = 4

@Composable
internal fun PlaylistList(
    playlists: List<UnifiedPlaylist>,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    addToPlaylist: AddToPlaylistController,
    onOpenPlaylist: (String) -> Unit,
    onOpenImport: () -> Unit
) {
    var naming by remember { mutableStateOf(false) }
    var actionsForPlaylist by remember { mutableStateOf<UnifiedPlaylist?>(null) }

    if (naming) {
        NewPlaylistDialog(
            onConfirm = { name ->
                naming = false
                viewModel.createPlaylist(name)
            },
            onDismiss = { naming = false }
        )
    }

    actionsForPlaylist?.let { playlist ->
        PlaylistActionsSheet(
            playlist = playlist,
            onPlay = { viewModel.openPlaylist(playlist) },
            onPlayNext = { viewModel.playPlaylistNext(playlist) },
            onAddToQueue = { viewModel.addPlaylistToQueue(playlist) },
            onAddToPlaylist = { viewModel.addPlaylistToAnother(playlist, addToPlaylist) },
            onShare = { viewModel.sharePlaylist(playlist) }
                .takeIf { viewModel.canShare(playlist.source) },
            onDelete = { viewModel.deletePlaylist(playlist) }
                .takeIf { playlist.source == SourceType.LOCAL || viewModel.canCreatePlaylists },
            onDismiss = { actionsForPlaylist = null }
        )
    }

    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmptyState(
                    title = "No playlists",
                    message = "Playlists from your sources and imported playlists appear here."
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    if (viewModel.canCreatePlaylists) {
                        Button(
                            onClick = { naming = true },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("New playlist", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenImport,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Text("Import", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        return
    }

    var playlistPageSize by remember(playlists) { mutableIntStateOf(PLAYLISTS_PAGE_SIZE) }
    val visiblePlaylists = remember(playlists, playlistPageSize) { playlists.take(playlistPageSize) }
    val hasMorePlaylists = visiblePlaylists.size < playlists.size
    val listState = rememberLazyListState()

    // Driven by the scroll position rather than by a row composing: an effect inside the item
    // lambda keyed on the page size it mutates walks the whole list on the first frame.
    LaunchedEffect(listState, playlists) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                // One leading action row sits above the playlists.
                if (lastVisible - 1 >= playlistPageSize - PAGE_PREFETCH_DISTANCE) {
                    playlistPageSize =
                        (playlistPageSize + PLAYLISTS_PAGE_SIZE).coerceAtMost(playlists.size)
                }
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding.listInset(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "playlist_actions", contentType = "action") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (viewModel.canCreatePlaylists) {
                    FilledTonalButton(
                        onClick = { naming = true },
                        modifier = Modifier.weight(1f),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                        Text(text = "New playlist", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                FilledTonalButton(
                    onClick = onOpenImport,
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                    Text(text = "Import", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        items(visiblePlaylists, key = { it.id }, contentType = { "playlist" }) { playlist ->
            PlaylistRow(
                playlist = playlist,
                onClick = { onOpenPlaylist(playlist.id) },
                onLongPress = { actionsForPlaylist = playlist }
            )
        }
        if (hasMorePlaylists) {
            items(count = 2, key = { "playlist_skeleton_$it" }) {
                SkeletonRow(leadingSize = 48.dp, leadingShape = MaterialTheme.shapes.extraSmall)
            }
        }
    }
}
