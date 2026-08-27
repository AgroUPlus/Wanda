package com.wander.android.ui.screens.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import com.wander.android.data.model.ArtistAlbumSection
import com.wander.android.data.model.ArtistSection
import com.wander.android.data.model.ArtistTrackSection
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.screens.library.AlbumCard
import androidx.compose.ui.unit.dp

/**
 * The shelves the backend arranged, in the backend's order and under the backend's headings.
 *
 * Nothing here is normalised into a fixed layout. YouTube Music gives an artist "Songs", "Albums",
 * "Singles" and sometimes "Videos"; Subsonic gives "Albums" and nothing else; an artist with no
 * singles has no singles shelf. Rendering a fixed set would mean inventing empty sections, which
 * would state something about the artist that is not true.
 *
 * Keys are prefixed with the section title so two shelves that both contain the same record — a
 * single that also appears on an album shelf — do not collide in the list.
 */
internal fun LazyListScope.artistSections(
    sections: List<ArtistSection>,
    onOpenAlbum: (String) -> Unit,
    onPlayTrack: (UnifiedTrack) -> Unit,
    onLongPressTrack: (UnifiedTrack) -> Unit,
    onToggleLike: (UnifiedTrack) -> Unit
) {
    sections.forEach { section ->
        item(key = "section-title-${section.title}", contentType = "section-title") {
            ArtistSectionTitle(section.title)
        }

        when (section) {
            is ArtistAlbumSection -> item(
                key = "section-albums-${section.title}",
                contentType = "album-row"
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(section.albums, key = { it.id }, contentType = { "album" }) { album ->
                        AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                    }
                }
            }

            is ArtistTrackSection -> items(
                items = section.tracks,
                key = { track -> "section-${section.title}-${track.id}" },
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
}
