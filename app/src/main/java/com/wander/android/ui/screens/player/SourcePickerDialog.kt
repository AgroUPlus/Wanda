package com.wander.android.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.model.isPlayableOffline
import com.wander.android.ui.components.AudioQualityBadge

/**
 * Where this song is playing from, and everywhere else it could.
 *
 * The player has always printed the source name; this makes it a control. The same recording
 * usually exists in several places — a file on the phone, a stream from your own server, a copy on
 * YouTube Music — and which one is playing was, until now, decided entirely by whichever list the
 * user happened to tap it in.
 *
 * Centred rather than anchored to the label like `SpeedPitchPopup` is. Speed and pitch adjust the
 * thing you are already hearing and belong beside their control; this changes what is being played,
 * which is a decision about the track itself and earns the middle of the screen.
 *
 * Switching keeps the playback position, so choosing a better copy mid-song is a change of source
 * and not a restart.
 */
@Composable
internal fun SourcePickerDialog(
    current: UnifiedTrack,
    renditions: List<UnifiedTrack>,
    isSearching: Boolean,
    onSelect: (UnifiedTrack) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Play from",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp)
                )

                Box(modifier = Modifier.height(8.dp))

                renditions.forEach { rendition ->
                    RenditionRow(
                        rendition = rendition,
                        isCurrent = rendition.id == current.id,
                        onClick = { onSelect(rendition) }
                    )
                }

                // Only the *other* sources are still being looked for; the one playing is known.
                // Shown under the list rather than replacing it, so the row you are on stays
                // readable while the rest arrive.
                if (isSearching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = "Looking on your other sources…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (renditions.size == 1) {
                    Text(
                        text = "This is the only copy your sources have.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenditionRow(
    rendition: UnifiedTrack,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // The current source is not tappable: choosing what is already playing would restart
            // the track for no reason.
            .clickable(enabled = !isCurrent, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rendition.source.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                // The fact that actually decides this choice on mobile data or underground. A
                // download keeps its original source, so without saying so the row is
                // indistinguishable from the stream it was made from.
                if (rendition.isPlayableOffline()) {
                    Text(
                        text = "On this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                rendition.audioQualityLabel?.let { quality ->
                    AudioQualityBadge(quality = quality)
                }
            }
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Playing from here",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
