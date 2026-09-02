package com.wander.android.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedAlbum
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.scrollingTitle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: UnifiedAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Long press opens the actions sheet. Null on the screens that have nowhere to show one, so
     * the gesture does nothing rather than half-working.
     */
    onLongClick: (() -> Unit)? = null,
    /** Nominal cell width. The grid is `Adaptive(156.dp)`, so cells never fall below this. */
    artworkSize: Dp = 160.dp
) {
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Artwork(
            url = album.coverArtUrl,
            contentDescription = album.title,
            sizeDp = artworkSize,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 8.dp).scrollingTitle()
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.scrollingTitle()
        )
    }
}
