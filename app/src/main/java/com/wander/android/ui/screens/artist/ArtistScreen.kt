package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.wander.android.ui.components.DetailHeader
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.TrackActionsSheet
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset
import com.wander.android.ui.screens.library.AlbumCard

/**
 * An artist: their discography, then their tracks, gathered across every connected backend.
 *
 * Keyed by name rather than by id — see [com.wander.android.data.repository.CatalogRepository].
 */
@Composable
fun ArtistScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel()
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<UnifiedTrack?>(null) }

    actionsFor?.let { track ->
        TrackActionsSheet(
            track = track,
            isLiked = track.isLiked,
            onPlay = { viewModel.play(tracks.indexOf(track).coerceAtLeast(0)) },
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

        if (albums.isEmpty() && tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    LoadingIndicator()
                } else {
                    EmptyState(
                        title = "Nothing by ${viewModel.artist}",
                        message = "None of your connected sources has anything by this artist."
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header", contentType = "header") {
                DetailHeader(
                    title = viewModel.artist,
                    subtitle = artistSubtitle(albums.size, tracks.size),
                    artworkUrl = viewModel.image(),
                    // Round, so an artist never reads as a record at a glance.
                    artworkShape = CircleShape,
                    onPlay = viewModel::playTop,
                    onShuffle = viewModel::shuffle,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (albums.isNotEmpty()) {
                item(key = "albums-title", contentType = "section-title") {
                    SectionTitle("Discography")
                }
                item(key = "albums", contentType = "album-row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        items(albums, key = { it.id }, contentType = { "album" }) { album ->
                            AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                        }
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                item(key = "tracks-title", contentType = "section-title") {
                    SectionTitle("Tracks")
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
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

private fun artistSubtitle(albumCount: Int, trackCount: Int): String = listOfNotNull(
    albumCount.takeIf { it > 0 }?.let { "$it album${if (it == 1) "" else "s"}" },
    trackCount.takeIf { it > 0 }?.let { "$it track${if (it == 1) "" else "s"}" }
).joinToString(" · ")
