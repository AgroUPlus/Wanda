package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistPage
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.RelatedArtist
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.screens.library.AlbumCard

/** How many songs the page shows before "Show all". */
internal const val TopSongsPreview = 5

/**
 * The artist page's fixed buckets, in fixed order.
 *
 * The order is the claim: what they are best known for, then what they released, then the
 * peripheral material, then who they sound like. A bucket with nothing in it draws nothing — the
 * layout is fixed, but it never asserts an artist has no singles by showing an empty singles shelf.
 *
 * Anything the merger could not classify comes last, still under the backend's own heading. See
 * [com.wander.android.data.repository.ArtistPageMerger].
 */
internal fun LazyListScope.artistPageSections(
    page: ArtistPage,
    showAllSongs: Boolean,
    expandedShelves: Map<String, List<UnifiedAlbum>>,
    loadingShelf: String?,
    onToggleShowAllSongs: () -> Unit,
    onExpandShelf: (ArtistAlbumSection) -> Unit,
    onPlaySong: (Int) -> Unit,
    onPlayTrack: (UnifiedTrack) -> Unit,
    onLongPressTrack: (UnifiedTrack) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String, String?) -> Unit
) {
    if (page.topSongs.isNotEmpty()) {
        val shown = if (showAllSongs) page.topSongs else page.topSongs.take(TopSongsPreview)
        item(key = "songs-title", contentType = "section-title") {
            ArtistSectionTitle(
                text = "Top songs",
                action = if (page.topSongs.size > TopSongsPreview) {
                    if (showAllSongs) "Show less" else "Show all"
                } else {
                    null
                },
                onAction = onToggleShowAllSongs
            )
        }
        // Keyed by track id: this bucket is a merge across every backend, so a song's surviving
        // copy can change source under the user as later results land.
        // `shown` is a prefix of `topSongs`, so the row index *is* the index into the full list
        // the player is handed — no lookup, and no drift once "Show all" lengthens the list.
        itemsIndexed(
            items = shown,
            key = { _, track -> "song-${track.id}" },
            contentType = { _, _ -> "track" }
        ) { index, track ->
            TrackRow(
                track = track,
                onPlay = { onPlaySong(index) },
                onToggleLike = { onToggleLike(track) },
                onLongPress = { onLongPressTrack(track) },
                modifier = Modifier.animateItem()
            )
        }
    }

    albumShelf(page.albums, "albums", expandedShelves, loadingShelf, onExpandShelf, onOpenAlbum)
    albumShelf(page.singles, "singles", expandedShelves, loadingShelf, onExpandShelf, onOpenAlbum)

    if (page.videos.isNotEmpty()) {
        item(key = "videos-title", contentType = "section-title") { ArtistSectionTitle("Videos") }
        items(page.videos, key = { "video-${it.id}" }, contentType = { "track" }) { track ->
            TrackRow(
                track = track,
                onPlay = { onPlayTrack(track) },
                onToggleLike = { onToggleLike(track) },
                onLongPress = { onLongPressTrack(track) },
                modifier = Modifier.animateItem()
            )
        }
    }

    page.otherShelves.forEach { section ->
        item(key = "other-title-${section.title}", contentType = "section-title") {
            ArtistSectionTitle(section.title)
        }
        when (section) {
            is ArtistAlbumSection -> item(
                key = "other-albums-${section.title}",
                contentType = "album-row"
            ) {
                AlbumRow(section.albums, onOpenAlbum)
            }

            is ArtistTrackSection -> items(
                items = section.tracks,
                key = { "other-${section.title}-${it.id}" },
                contentType = { "track" }
            ) { track ->
                TrackRow(
                    track = track,
                    onPlay = { onPlayTrack(track) },
                    onToggleLike = { onToggleLike(track) },
                    onLongPress = { onLongPressTrack(track) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    if (page.related.isNotEmpty()) {
        item(key = "related-title", contentType = "section-title") {
            ArtistSectionTitle("Fans might also like")
        }
        item(key = "related", contentType = "artist-row") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                items(page.related, key = { it.id }, contentType = { "artist" }) { artist ->
                    RelatedArtistCard(artist, onClick = { onOpenArtist(artist.name, artist.id) })
                }
            }
        }
    }
}

/**
 * One album bucket, plus its "See all" when the backend told us the shelf was only a sample.
 *
 * Once expanded the shelf keeps the same list key but holds every record, so it stays a row rather
 * than becoming a second kind of layout the user has to re-learn.
 */
private fun LazyListScope.albumShelf(
    section: ArtistAlbumSection?,
    keyPrefix: String,
    expandedShelves: Map<String, List<UnifiedAlbum>>,
    loadingShelf: String?,
    onExpandShelf: (ArtistAlbumSection) -> Unit,
    onOpenAlbum: (String) -> Unit
) {
    if (section == null) return
    val albums = expandedShelves[section.title] ?: section.albums
    val canExpand = section.moreBrowseId != null && section.title !in expandedShelves

    item(key = "$keyPrefix-title", contentType = "section-title") {
        ArtistSectionTitle(
            text = section.title,
            action = "See all".takeIf { canExpand },
            isBusy = loadingShelf == section.title,
            onAction = { onExpandShelf(section) }
        )
    }
    item(key = "$keyPrefix-row", contentType = "album-row") {
        AlbumRow(albums, onOpenAlbum)
    }
}

@Composable
private fun AlbumRow(albums: List<UnifiedAlbum>, onOpenAlbum: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(albums, key = { it.id }, contentType = { "album" }) { album ->
            AlbumCard(
                album = album,
                onClick = { onOpenAlbum(album.id) },
                artworkSize = 132.dp,
                modifier = Modifier.width(148.dp)
            )
        }
    }
}

@Composable
internal fun ArtistSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    isBusy: Boolean = false,
    onAction: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )
        when {
            isBusy -> LoadingIndicator(modifier = Modifier.padding(horizontal = 12.dp))
            action != null -> TextButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(action) }
        }
    }
}
