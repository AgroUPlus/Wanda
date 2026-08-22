package com.wander.android.ui.screens.album

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import com.wander.android.ui.components.DetailHeader
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * One record, with its actual tracklist — reached by tapping the album name in the player, which
 * previously just ran a search for it.
 */
@Composable
fun AlbumScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    viewModel: AlbumViewModel = hiltViewModel()
) {
    val album by viewModel.album.collectAsStateWithLifecycle()
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

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding.headerInset())
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }

        // Nothing in Room and nothing from the server: an id that resolves to no album at all.
        if (album == null && tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    LoadingIndicator()
                } else {
                    EmptyState(
                        title = "Album unavailable",
                        message = "This album isn't on any of your connected sources."
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header", contentType = "header") {
                val current = album
                DetailHeader(
                    title = current?.title ?: tracks.firstOrNull()?.album.orEmpty(),
                    subtitle = albumSubtitle(
                        artist = current?.artist ?: tracks.firstOrNull()?.artist.orEmpty(),
                        year = current?.year,
                        trackCount = tracks.size
                    ),
                    artworkUrl = current?.coverArtUrl
                        ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
                    artworkShape = MaterialTheme.shapes.large,
                    onPlay = viewModel::playAll,
                    onShuffle = viewModel::shuffle,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // The artist name is a way *out* of this page, into the rest of their work.
            item(key = "artist-link", contentType = "artist-link") {
                val artist = album?.artist ?: tracks.firstOrNull()?.artist
                if (!artist.isNullOrBlank()) {
                    ArtistLinkRow(artist = artist, onClick = { onOpenArtist(artist) })
                }
            }

            itemsIndexed(
                items = tracks,
                key = { _, track -> track.id },
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

/** "Nirvana · 1991 · 13 tracks", skipping whatever is unknown. */
private fun albumSubtitle(artist: String, year: Int?, trackCount: Int): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    year?.toString(),
    trackCount.takeIf { it > 0 }?.let { "$it track${if (it == 1) "" else "s"}" }
).joinToString(" · ")
