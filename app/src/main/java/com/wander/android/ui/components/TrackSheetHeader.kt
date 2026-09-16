package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack

/**
 * Common track header used at the top of bottom action sheets ([TrackActionsSheet] and
 * [com.wander.android.ui.screens.player.NowPlayingMenuDrawer]).
 */
@Composable
internal fun TrackSheetHeader(
    track: UnifiedTrack,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Artwork(
            url = track.artworkUrl,
            contentDescription = null,
            sizeDp = 48.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(48.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}
