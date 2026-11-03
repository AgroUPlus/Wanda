package com.wander.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.BackLayers
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.ScrollToTopOnReselect
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.nestedListInset
import com.wander.android.ui.components.rememberLastLive
import com.wander.android.ui.components.selection.TrackSelectionState
import com.wander.android.ui.navigation.InterceptReselect

/**
 * The library by genre tag, and one genre's tracks once it is opened.
 *
 * The open genre is page state rather than a route: it is a filter on this list, and Back closes
 * it the way it closes the search field, without leaving the Library. The genre is laid over the
 * list ([BackLayers]) so a back swipe can show the list coming back.
 */
@Composable
internal fun LibraryGenresPage(
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel,
    browse: LibraryBrowseViewModel,
    isSyncing: Boolean,
    onLongPress: (UnifiedTrack) -> Unit,
    selection: TrackSelectionState
) {
    val openGenre by browse.openGenre.collectAsStateWithLifecycle()
    // An open genre is a page opened over the genre list, so a reselect closes it, as back does —
    // the same thing a tap does over an album.
    InterceptReselect(consume = {
        val open = openGenre != null
        if (open) browse.openGenre(null)
        open
    }) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryListToolbar(LibraryTab.GENRES, browse)
            // The genre opens over the list, which stays underneath with its scroll kept, so a back
            // swipe shows the list coming forward as the genre shrinks away.
            BackLayers(
                page = openGenre,
                onBack = { browse.openGenre(null) },
                base = { GenreList(contentPadding, browse) }
            ) { genre ->
                val live by browse.genreTracks.collectAsStateWithLifecycle()
                val tracks = rememberLastLive(live, live = openGenre == genre)
                Column(modifier = Modifier.fillMaxSize()) {
                    GenreHeader(
                        genre = genre,
                        canPlay = tracks.isNotEmpty(),
                        onBack = { browse.openGenre(null) },
                        onPlay = { viewModel.play(tracks, 0) },
                        onShuffle = { viewModel.play(tracks.shuffled(), 0) }
                    )
                    TrackList(tracks, LibraryTab.GENRES, isSyncing, contentPadding, viewModel, onLongPress, selection)
                }
            }
        }
    }
}

@Composable
private fun GenreList(contentPadding: PaddingValues, browse: LibraryBrowseViewModel) {
    val genres by browse.genres.collectAsStateWithLifecycle()
    if (genres.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = R.string.library_genres_empty_title,
                message = R.string.library_genres_empty_message,
                icon = Icons.Rounded.Category
            )
        }
        return
    }
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(listState)
    LazyColumn(
        state = listState,
        contentPadding = contentPadding.nestedListInset(),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(genres, key = { _, genre -> genre.name }) { index, genre ->
            Column(
                modifier = Modifier
                    .groupedListItem(index, genres.size)
                    .fillMaxWidth()
                    .clickable { browse.openGenre(genre.name) }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(R.plurals.library_track_count, genre.trackCount, genre.trackCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GenreHeader(
    genre: String,
    canPlay: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, bottom = 4.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
        }
        Text(
            text = genre,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onShuffle, enabled = canPlay) {
            Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.library_shuffle_all))
        }
        IconButton(onClick = onPlay, enabled = canPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.common_play_all))
        }
    }
}
