package com.wander.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.database.dao.LibraryArtistRow
import com.wander.android.ui.components.ArtistMonogram
import com.wander.android.ui.components.nestedListInset

/**
 * The Artists tab as a grid: each artist a round monogram with the name and track count under it,
 * three across on a phone. Monograms for the reason [ArtistMonogram] gives — no photograph that
 * cannot be checked.
 */
@Composable
internal fun ArtistGrid(
    artists: List<LibraryArtistRow>,
    contentPadding: PaddingValues,
    state: LazyGridState,
    onOpenArtist: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val inset = contentPadding.nestedListInset()
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(minSize = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = inset.calculateBottomPadding()),
        modifier = modifier.fillMaxSize()
    ) {
        items(artists, key = { it.name }, contentType = { "artist" }) { artist ->
            ArtistCell(artist, onClick = { onOpenArtist(artist.name, artist.artistId) })
        }
    }
}

@Composable
private fun ArtistCell(artist: LibraryArtistRow, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        ArtistMonogram(
            name = artist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
        )
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (artist.trackCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.library_track_count, artist.trackCount, artist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
