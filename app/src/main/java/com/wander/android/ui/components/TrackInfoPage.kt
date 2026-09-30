package com.wander.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.UnifiedTrack

private data class InfoItem(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val value: String
)

/**
 * The second page of a track's contextual menu — see `TrackActionsSheet`'s own pager.
 *
 * Grouped into one continuous surface using [groupedItemShape] and [GroupedItemGap] — the same
 * grouped-card idiom Settings and lists across the app use.
 *
 * The title is deliberately omitted here: it already sits in the sheet's header right above this
 * pager, so repeating it would waste vertical space.
 */
@Composable
internal fun TrackInfoPage(track: UnifiedTrack, modifier: Modifier = Modifier) {
    val items = buildList {
        add(InfoItem(Icons.Rounded.Person, R.string.track_info_artist, track.artist))
        track.album?.takeIf { it.isNotBlank() }?.let {
            add(InfoItem(Icons.Rounded.Album, R.string.track_info_album, it))
        }
        if (track.durationMs > 0) {
            add(InfoItem(Icons.Rounded.Schedule, R.string.track_info_duration, track.durationFormatted))
        }
        trackPositionLabel(track)?.let {
            add(InfoItem(Icons.Rounded.Tag, R.string.track_info_track, it))
        }
        track.year?.takeIf { it > 0 }?.let {
            add(InfoItem(Icons.Rounded.CalendarToday, R.string.track_info_year, it.toString()))
        }
        track.genre?.takeIf { it.isNotBlank() }?.let {
            add(InfoItem(Icons.Rounded.MusicNote, R.string.track_info_genre, it))
        }
        track.audioQualityLabel?.let {
            add(InfoItem(Icons.Rounded.GraphicEq, R.string.track_info_quality, it))
        }
        add(InfoItem(Icons.Rounded.Cloud, R.string.track_info_source, track.source.displayName))
        statusLabel(track)?.let {
            add(InfoItem(Icons.Rounded.Favorite, R.string.track_info_status, it))
        }
        val views = track.extraData["views"] ?: track.extraData["viewCount"]
        if (!views.isNullOrBlank()) {
            add(InfoItem(Icons.Rounded.Visibility, R.string.track_info_views, views))
        } else if (track.playCount > 0) {
            add(InfoItem(Icons.Rounded.PlayArrow, R.string.track_info_plays, track.playCount.toString()))
        }
        val likes = track.extraData["likes"] ?: track.extraData["likeCount"]
        if (!likes.isNullOrBlank()) {
            add(InfoItem(Icons.Rounded.Favorite, R.string.track_info_likes, likes))
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items.forEachIndexed { index, item ->
            InfoRow(
                icon = item.icon,
                labelRes = item.labelRes,
                value = item.value,
                shape = groupedItemShape(index, items.size)
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    @StringRes labelRes: Int,
    value: String,
    shape: Shape
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun trackPositionLabel(track: UnifiedTrack): String? {
    val number = track.trackNumber ?: return null
    val disc = track.discNumber?.takeIf { it > 1 }
    return if (disc != null) {
        stringResource(R.string.track_info_disc_track, disc, number)
    } else {
        stringResource(R.string.track_info_track_number, number)
    }
}

@Composable
private fun statusLabel(track: UnifiedTrack): String? = listOfNotNull(
    stringResource(R.string.track_info_status_liked).takeIf { track.isLiked },
    stringResource(R.string.track_info_status_downloaded).takeIf { track.isDownloaded },
    stringResource(R.string.track_info_status_cached).takeIf { track.isCached && !track.isDownloaded }
).takeIf { it.isNotEmpty() }?.joinToString(" · ")
