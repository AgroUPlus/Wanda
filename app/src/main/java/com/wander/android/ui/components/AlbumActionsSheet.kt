package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedAlbum

/**
 * What you can do with a record, on a long press.
 *
 * The same shape as [PlaylistActionsSheet] deliberately: these are the same gesture on two kinds of
 * collection, and a user who has learned one should not have to learn the other.
 *
 * Every action is optional and absent when the screen cannot do it, rather than present and
 * disabled — a greyed-out row asks the user to work out why, and the answer is usually "this
 * backend does not do that", which is not something they can act on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumActionsSheet(
    album: UnifiedAlbum,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDismiss: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
) {
    WandaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Artwork(
                    url = album.coverArtUrl,
                    contentDescription = null,
                    sizeDp = 52.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(52.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.scrollingTitle()
                    )
                    Text(
                        text = album.subtitleLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            ActionButtonGroup(
                modifier = Modifier.padding(top = 8.dp),
                actions = buildList {
                    add(MenuAction(Icons.Rounded.PlayArrow, "Play", ActionEmphasis.PRIMARY) { onPlay(); onDismiss() })
                    add(MenuAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next", ActionEmphasis.SECONDARY) { onPlayNext(); onDismiss() })
                    // Always offered when present, unlike a track's share. An album link describes
                    // the record rather than naming a server, so it works from a source that cannot
                    // mint links at all — the local library included. See `ShareRepository.shareAlbum`.
                    onShare?.let { add(MenuAction(Icons.Rounded.Share, "Share", ActionEmphasis.ICON) { it(); onDismiss() }) }
                    onDownload?.let { add(MenuAction(Icons.Rounded.Download, "Download", ActionEmphasis.ICON) { it(); onDismiss() }) }
                    onAddToPlaylist?.let { add(MenuAction(Icons.Rounded.LibraryAdd, "Add to playlist", ActionEmphasis.ICON) { it(); onDismiss() }) }
                    add(MenuAction(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") { onAddToQueue(); onDismiss() })
                    onGoToArtist?.let { add(MenuAction(Icons.Rounded.Person, album.artist) { it(); onDismiss() }) }
                }
            )
        }
    }
}

/** "Radiohead • 2000 • 10 tracks", dropping whatever the source did not fill in. */
private fun UnifiedAlbum.subtitleLine(): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    year?.takeIf { it > 0 }?.toString(),
    songCount.takeIf { it > 0 }?.let { "$it tracks" }
).joinToString(" • ")
