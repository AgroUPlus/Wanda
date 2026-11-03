package com.wander.android.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.RelatedArtist
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedArtist
import com.wander.android.ui.components.albumArtKey
import com.wander.android.ui.screens.artist.RelatedArtistCard
import com.wander.android.ui.screens.library.AlbumCard

/**
 * The artists a search matched, as one row of round portraits.
 *
 * Opened by name *and* id: the artist route gathers everything under the name across backends,
 * and the id is what tells it which of two artists sharing that name this one is.
 */
internal fun LazyListScope.artistResultsSection(
    artists: List<UnifiedArtist>,
    onOpenArtist: (String, String?) -> Unit
) {
    if (artists.isEmpty()) return
    searchSectionHeader(key = "header_artists", title = R.string.search_section_artists)
    item(key = "row_artists", contentType = "artist_row") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.animateItem()
        ) {
            itemsIndexed(artists, key = { _, artist -> artist.id }, contentType = { _, _ -> "artist" }) { _, artist ->
                RelatedArtistCard(
                    artist = RelatedArtist(id = artist.id, name = artist.name, imageUrl = artist.coverArtUrl),
                    onClick = { onOpenArtist(artist.name, artist.id) }
                )
            }
        }
    }
}

/** The records a search matched — your library's first — as one row of covers. */
internal fun LazyListScope.albumResultsSection(
    albums: List<UnifiedAlbum>,
    onLongPressAlbum: (UnifiedAlbum) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    if (albums.isEmpty()) return
    searchSectionHeader(key = "header_albums", title = R.string.search_section_albums)
    item(key = "row_albums", contentType = "album_row") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.animateItem()
        ) {
            itemsIndexed(albums, key = { _, album -> album.id }, contentType = { _, _ -> "album" }) { index, album ->
                AlbumCard(
                    album = album,
                    index = index,
                    onClick = { onOpenAlbum(album.id) },
                    onLongClick = { onLongPressAlbum(album) },
                    artworkSize = 132.dp,
                    sharedKey = albumArtKey(album.id),
                    modifier = Modifier.width(148.dp)
                )
            }
        }
    }
}
