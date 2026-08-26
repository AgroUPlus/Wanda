package com.wander.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedPlaylist
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.scrollingTitle

@Composable
fun PlaylistRow(
    playlist: UnifiedPlaylist,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Artwork(
            url = playlist.coverArtUrl,
            contentDescription = null,
            sizeDp = 52.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(52.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
            Text(
                text = remember(playlist) { playlist.subtitle() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
        }
    }
}

/** Song count is unknown for some sources, so it is only shown when the source reported one. */
private fun UnifiedPlaylist.subtitle(): String = listOfNotNull(
    source.displayName,
    songCount.takeIf { it > 0 }?.let { "$it tracks" },
    comment?.takeIf { it.isNotBlank() }
).joinToString(" · ")
