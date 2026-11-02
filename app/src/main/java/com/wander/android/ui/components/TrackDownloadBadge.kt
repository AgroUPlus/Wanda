package com.wander.android.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedTrack

/**
 * A small mark leading a [TrackRow]'s subtitle: a tick once the track is on the device, an arrow
 * while a download asked for by hand is waiting. Nothing for a local file, which is on the device
 * by definition, or for anything not downloaded at all.
 */
@Composable
internal fun TrackDownloadBadge(track: UnifiedTrack) {
    if (track.source == SourceType.LOCAL) return
    val marks = LocalDownloadMarks.current
    val (icon, description, tint) = when {
        marks.isDownloaded(track) -> Triple(
            Icons.Rounded.DownloadDone,
            R.string.track_download_state_downloaded,
            MaterialTheme.colorScheme.primary
        )
        marks.isQueued(track) -> Triple(
            Icons.Rounded.Download,
            R.string.track_download_state_queued,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = tint,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(4.dp))
}
