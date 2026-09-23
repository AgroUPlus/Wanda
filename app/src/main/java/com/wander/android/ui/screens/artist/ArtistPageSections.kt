package com.wander.android.ui.screens.artist

import com.wander.android.ui.components.groupedListItem
import com.wander.android.data.repository.newestFirst
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistPage
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.RelatedArtist
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.SkeletonCard
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
    onOpenAlbum: (String) -> Unit,
    onLongPressAlbum: (UnifiedAlbum) -> Unit = {},
    onOpenArtist: (String, String?) -> Unit
) {
    if (page.topSongs.isNotEmpty()) {
        val shown = if (showAllSongs) page.topSongs else page.topSongs.take(TopSongsPreview)
        item(key = "songs-title", contentType = SECTION_TITLE) {
            ArtistSectionTitle(
                text = stringResource(R.string.artist_top_songs),
                action = if (page.topSongs.size > TopSongsPreview) {
                    if (showAllSongs) "Show less" else "Show all"
                } else {
                    null
                },
                actionSelected = showAllSongs,
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
                onLongPress = { onLongPressTrack(track) },
                modifier = Modifier.animateItem().groupedListItem(index, shown.size)
            )
        }
    }

    if (page.episodes.isNotEmpty()) {
        item(key = "episodes-title", contentType = SECTION_TITLE) {
            ArtistSectionTitle(stringResource(R.string.artist_episodes))
        }
        // One at a time, like the Podcasts tab: an episode is not the start of a queue of others.
        itemsIndexed(page.episodes, key = { _, it -> "episode-${it.id}" }, contentType = { _, _ -> "track" }) { index, track ->
            TrackRow(
                track = track,
                onPlay = { onPlayTrack(track) },
                onLongPress = { onLongPressTrack(track) },
                modifier = Modifier.animateItem().groupedListItem(index, page.episodes.size)
            )
        }
    }

    albumShelf(page.albums, "albums", expandedShelves, loadingShelf, onExpandShelf, onOpenAlbum, onLongPressAlbum)
    albumShelf(page.singles, "singles", expandedShelves, loadingShelf, onExpandShelf, onOpenAlbum, onLongPressAlbum)

    if (page.videos.isNotEmpty()) {
        item(key = "videos-title", contentType = SECTION_TITLE) { ArtistSectionTitle("Videos") }
        itemsIndexed(page.videos, key = { _, it -> "video-${it.id}" }, contentType = { _, _ -> "track" }) { index, track ->
            TrackRow(
                track = track,
                onPlay = { onPlayTrack(track) },
                onLongPress = { onLongPressTrack(track) },
                modifier = Modifier.animateItem().groupedListItem(index, page.videos.size)
            )
        }
    }

    page.otherShelves.forEach { section ->
        item(key = "other-title-${section.title}", contentType = SECTION_TITLE) {
            ArtistSectionTitle(section.title)
        }
        when (section) {
            is ArtistAlbumSection -> item(
                key = "other-albums-${section.title}",
                contentType = "album-row"
            ) {
                AlbumRow(section.albums, onOpenAlbum, onLongPressAlbum)
            }

            is ArtistTrackSection -> itemsIndexed(
                items = section.tracks,
                key = { _, it -> "other-${section.title}-${it.id}" },
                contentType = { _, _ -> "track" }
            ) { index, track ->
                TrackRow(
                    track = track,
                    onPlay = { onPlayTrack(track) },
                    onLongPress = { onLongPressTrack(track) },
                    modifier = Modifier.animateItem().groupedListItem(index, section.tracks.size)
                )
            }
        }
    }

    if (page.related.isNotEmpty()) {
        item(key = "related-title", contentType = SECTION_TITLE) {
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
    onOpenAlbum: (String) -> Unit,
    onLongPressAlbum: (UnifiedAlbum) -> Unit
) {
    if (section == null) return
    val albums = expandedShelves[section.title]?.newestFirst() ?: section.albums
    val canExpand = section.moreBrowseId != null && section.title !in expandedShelves

    item(key = "$keyPrefix-title", contentType = SECTION_TITLE) {
        ArtistSectionTitle(
            text = section.title,
            action = "See all".takeIf { canExpand },
            isBusy = loadingShelf == section.title,
            onAction = { onExpandShelf(section) }
        )
    }
    item(key = "$keyPrefix-row", contentType = "album-row") {
        AlbumRow(albums, onOpenAlbum, onLongPressAlbum, loadingMore = loadingShelf == section.title)
    }
}

@Composable
private fun AlbumRow(
    albums: List<UnifiedAlbum>,
    onOpenAlbum: (String) -> Unit,
    onLongPressAlbum: (UnifiedAlbum) -> Unit,
    /** The rest of the shelf is on its way: placeholders hold its place at the end of the row. */
    loadingMore: Boolean = false
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        itemsIndexed(albums, key = { _, album -> album.id }, contentType = { _, _ -> "album" }) { index, album ->
            AlbumCard(
                album = album,
                index = index,
                onClick = { onOpenAlbum(album.id) },
                onLongClick = { onLongPressAlbum(album) },
                // The artist is the page itself, so the line under each record says when it came
                // out instead — which is also the order the shelf is in.
                subtitle = album.year?.toString().orEmpty(),
                artworkSize = 132.dp,
                modifier = Modifier.width(148.dp).animateItem()
            )
        }
        if (loadingMore) {
            items(count = 3, key = { "album-skeleton-$it" }, contentType = { "skeleton" }) {
                SkeletonCard(modifier = Modifier.width(148.dp).animateItem())
            }
        }
    }
}

/** Recycling hint shared by every section heading in the artist list. */
private const val SECTION_TITLE = "section-title"
