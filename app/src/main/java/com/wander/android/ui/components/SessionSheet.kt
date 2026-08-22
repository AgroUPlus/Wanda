package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroHandoffState

/**
 * The other device's session, opened from the Home header.
 *
 * A sheet rather than a dialog: it is a thing you act on and dismiss, it carries artwork at a size
 * worth looking at, and it does not steal the screen the way a dialog does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionSheet(
    handoff: AgroHandoffState,
    deviceName: String,
    isLive: Boolean,
    isResuming: Boolean,
    artworkUrl: String?,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            LiveBadge(deviceName = deviceName, isLive = isLive)

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The session's own artwork when the sending device provided it, otherwise the
                // cover of the track once this device has resolved it — a placeholder is the last
                // resort, not the default.
                Artwork(
                    url = artworkUrl ?: handoff.artworkUrl,
                    contentDescription = null,
                    sizeDp = 96.dp,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(96.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = handoff.trackTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = handoff.artistName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    handoff.albumName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Text(
                text = buildString {
                    append("Picks up at ${handoff.positionMs.asClock()}")
                    if (handoff.queue.size > 1) {
                        append(" · ${handoff.queue.size} tracks in the queue")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onResume,
                enabled = !isResuming,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isResuming) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                }
                Text(
                    text = if (isResuming) "Finding it here…" else "Continue on this device",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        }
    }
}

/** "Listening on Breezy Koala", with the green dot when that device is actually live. */
@Composable
private fun LiveBadge(deviceName: String, isLive: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isLive) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isLive) ListeningGreen else MaterialTheme.colorScheme.outlineVariant
                    )
            ) {}
            Text(
                text = if (isLive) "Listening on $deviceName" else "Last played on $deviceName",
                style = MaterialTheme.typography.labelLarge,
                color = if (isLive) ListeningGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** `positionMs` as `m:ss`, so "picks up at" reads like a timestamp rather than a number. */
private fun Long.asClock(): String {
    val totalSeconds = this / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
