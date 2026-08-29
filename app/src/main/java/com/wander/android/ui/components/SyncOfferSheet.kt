package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import com.wander.android.data.repository.FetchProgress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.MissingTrack
import com.wander.android.data.sources.agro.SyncRoute

/**
 * Everything on offer, with room to read it.
 *
 * The card that opens this is a summary — a count and a route — because three lines of titles
 * beside a button could never say much and what it did say was cut off. This is where the detail
 * goes: every track, grouped by the record it came from, in a sheet that scrolls.
 *
 * Grouped by album rather than listed flat because that is how an offer of a hundred and thirty
 * tracks is actually understood: "the rest of that record", not a hundred and thirty song titles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncOfferSheet(
    tracks: List<MissingTrack>,
    isFetching: Boolean,
    progress: FetchProgress,
    /** How these would travel, measured rather than assumed. */
    route: SyncRoute?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    if (tracks.isEmpty()) return

    val albums = remember(tracks) {
        tracks.groupBy { it.album?.takeIf(String::isNotBlank) ?: "Unknown album" }
            .toList()
            .sortedByDescending { (_, songs) -> songs.size }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = if (tracks.size == 1) "1 track available" else "${tracks.size} tracks available",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (progress.total > 0 && isFetching) {
                    "${progress.done.size} of ${progress.total} downloaded" +
                        (progress.route?.let { " · ${it.label}" } ?: "")
                } else if (route != null) {
                    "From ${route.label.lowercase()}, across " +
                        if (albums.size == 1) "1 album" else "${albums.size} albums"
                } else {
                    "From your other devices, across " +
                        if (albums.size == 1) "1 album" else "${albums.size} albums"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (progress.total > 0 && isFetching) {
                LinearWavyProgressIndicator(
                    progress = { progress.done.size.toFloat() / progress.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 4.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            albums.forEach { (album, songs) ->
                item(key = "album:$album") {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 2.dp)
                            .scrollingTitle()
                    )
                }
                items(songs, key = { it.contentHash }) { track ->
                    val state = when {
                        track.contentHash in progress.done -> TrackFetchState.DONE
                        track.contentHash == progress.current -> TrackFetchState.FETCHING
                        else -> TrackFetchState.WAITING
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // A fixed-width slot, so titles stay aligned whether or not a row has a
                        // mark against it.
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (state) {
                                TrackFetchState.DONE -> Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                TrackFetchState.FETCHING -> LoadingIndicator(
                                    modifier = Modifier.size(16.dp)
                                )
                                TrackFetchState.WAITING -> Unit
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state == TrackFetchState.DONE) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                modifier = Modifier.scrollingTitle()
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.scrollingTitle()
                            )
                        }
                        Text(
                            text = formatSize(track.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onAccept,
            enabled = !isFetching,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = if (isFetching) "Syncing…" else "Get all ${tracks.size}",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Sizes are here to give a sense of the download, so one decimal is plenty. */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes > 0 -> "%.0f KB".format(bytes / 1_000.0)
    else -> ""
}

/** Where one track has got to in a run. */
private enum class TrackFetchState { DONE, FETCHING, WAITING }
