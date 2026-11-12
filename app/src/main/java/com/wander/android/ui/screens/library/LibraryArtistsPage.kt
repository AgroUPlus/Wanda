package com.wander.android.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.core.database.dao.LibraryArtistRow
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.ScrollToTopOnReselect
import com.wander.android.ui.components.fastScrollLetter
import com.wander.android.ui.components.groupedListItem
import com.wander.android.ui.components.monogramOf
import com.wander.android.ui.components.nestedListInset

/**
 * The library by artist, A to Z, each opening the artist's page — as rows or as a grid of
 * monograms, whichever the toolbar's switch last chose.
 *
 * Read from what Room already holds rather than asked of a source: a library that is local files,
 * a Navidrome server and a few YouTube likes has one list of artists, not three.
 */
@Composable
internal fun LibraryArtistsPage(
    contentPadding: PaddingValues,
    browse: LibraryBrowseViewModel,
    onOpenArtist: (String, String?) -> Unit
) {
    val artists by browse.artists.collectAsStateWithLifecycle()
    val views by browse.views.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        LibraryListToolbar(LibraryTab.ARTISTS, browse)
        if (artists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = R.string.library_artists_empty_title,
                    message = R.string.library_artists_empty_message,
                    icon = Icons.Rounded.Mic
                )
            }
            return@Column
        }
        // Always by name, so always lettered: the rail needs no order of its own to key on.
        val starts = rememberLetterStarts<LibraryArtistRow>(artists, Unit, ArtistLetter)
        val gutter = Modifier.padding(end = if (starts != null) LetterRailGutter else 0.dp)
        val edge = Modifier.padding(contentPadding.nestedListInset()).padding(vertical = 8.dp)
        Box(modifier = Modifier.fillMaxSize()) {
            if ((views[LibraryTab.ARTISTS] ?: LibraryTab.ARTISTS.defaultView) == LibraryView.GRID) {
                val gridState = rememberLazyGridState()
                ScrollToTopOnReselect(gridState)
                ArtistGrid(artists, contentPadding, gridState, onOpenArtist, gutter)
                starts?.let {
                    LetterRail(
                        starts = it,
                        letterAt = { railLetter(ArtistLetter(artists.getOrNull(gridState.firstVisibleItemIndex))) },
                        onJump = { index -> gridState.scrollToItem(index) },
                        modifier = Modifier.align(Alignment.TopEnd).then(edge)
                    )
                }
            } else {
                val listState = rememberLazyListState()
                ScrollToTopOnReselect(listState)
                LazyColumn(
                    state = listState,
                    contentPadding = contentPadding.nestedListInset(),
                    modifier = Modifier.fillMaxSize().then(gutter)
                ) {
                    itemsIndexed(artists, key = { _, artist -> artist.name }) { index, artist ->
                        ArtistRow(
                            artist = artist,
                            onClick = { onOpenArtist(artist.name, artist.artistId) },
                            modifier = Modifier.groupedListItem(index, artists.size)
                        )
                    }
                }
                starts?.let {
                    LetterRail(
                        starts = it,
                        letterAt = { railLetter(ArtistLetter(artists.getOrNull(listState.firstVisibleItemIndex))) },
                        onJump = { index -> listState.scrollToItem(index) },
                        modifier = Modifier.align(Alignment.TopEnd).then(edge)
                    )
                }
            }
        }
    }
}

/** The letter an artist is filed under: the first of their name. */
private val ArtistLetter: (LibraryArtistRow?) -> String? = { artist -> fastScrollLetter(artist?.name) }

@Composable
private fun ArtistRow(artist: LibraryArtistRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // A monogram rather than a photograph, for the reason `ArtistMonogram` gives.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
        ) {
            Text(
                text = monogramOf(artist.name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.trackCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.library_track_count,
                        artist.trackCount,
                        artist.trackCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
