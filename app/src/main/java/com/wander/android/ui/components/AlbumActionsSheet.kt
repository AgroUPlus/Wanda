package com.wander.android.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    ModalBottomSheet(
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AlbumSheetAction(Icons.Rounded.PlayArrow, "Play") { onPlay(); onDismiss() }
            AlbumSheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next") {
                onPlayNext(); onDismiss()
            }
            AlbumSheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") {
                onAddToQueue(); onDismiss()
            }
            onAddToPlaylist?.let {
                AlbumSheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") {
                    it(); onDismiss()
                }
            }
            onDownload?.let {
                AlbumSheetAction(Icons.Rounded.Download, "Download album") { it(); onDismiss() }
            }
            onGoToArtist?.let {
                AlbumSheetAction(Icons.Rounded.Person, "Go to ${album.artist}") { it(); onDismiss() }
            }
            onShare?.let {
                // Always offered, unlike a track's share. An album link describes the record rather
                // than naming a server, so it works from a source that cannot mint links at all —
                // the local library included. See `ShareRepository.shareAlbum`.
                AlbumSheetAction(Icons.Rounded.Share, "Share album") { it(); onDismiss() }
            }
        }
    }
}

/** "Radiohead • 2000 • 10 tracks", dropping whatever the source did not fill in. */
private fun UnifiedAlbum.subtitleLine(): String = listOfNotNull(
    artist.takeIf { it.isNotBlank() },
    year?.takeIf { it > 0 }?.toString(),
    songCount.takeIf { it > 0 }?.let { "$it tracks" }
).joinToString(" • ")

@Composable
private fun AlbumSheetAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
