package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.AddToPlaylistHost
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.screens.album.AlbumHero
import com.wander.android.ui.screens.album.AlbumSkeleton

@Composable
fun PlaylistScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }

    val addToPlaylist = AddToPlaylistHost()

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onStartRadio = { viewModel.startRadio(track) },
            onToggleLike = { viewModel.toggleLike(track) },
            onRemove = null,
            onOpenArtist = track.artist
                .takeIf { it.isNotBlank() }
                ?.let { artist -> { onOpenArtist(artist, track.artistId) } },
            onDismiss = { actionsFor = null },
            onShare = if (viewModel.canShare(track)) {
                { viewModel.share(track) }
            } else {
                null
            },
            onAddToPlaylist = if (addToPlaylist.canAdd(track)) {
                { addToPlaylist.open(track) }
            } else {
                null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding.headerInset())
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }

        if (isLoading && playlist == null && tracks.isEmpty()) {
            AlbumSkeleton(contentPadding.listInset())
            return@Column
        }

        if (playlist == null && tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Playlist unavailable",
                    message = "This playlist couldn't be loaded from any connected source."
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header", contentType = "header") {
                val current = playlist
                val trackCount = if (current?.songCount != null && current.songCount > 0) current.songCount else tracks.size
                val subtitle = listOfNotNull(
                    current?.source?.displayName,
                    "$trackCount track${if (trackCount == 1) "" else "s"}",
                    current?.comment?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")

                AlbumHero(
                    title = current?.name ?: "Playlist",
                    subtitle = subtitle,
                    artworkUrl = current?.coverArtUrl ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
                    onPlay = viewModel::playAll,
                    onShuffle = viewModel::shuffle,
                    onShare = viewModel::sharePlaylist.takeIf { viewModel.canSharePlaylist() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            itemsIndexed(
                items = tracks,
                key = { index, track -> "${track.id}_$index" },
                contentType = { _, _ -> "track" }
            ) { index, track ->
                TrackRow(
                    track = track,
                    onPlay = { viewModel.play(index) },
                    onToggleLike = { viewModel.toggleLike(track) },
                    onLongPress = { actionsFor = track }
                )
            }
        }
    }
}
