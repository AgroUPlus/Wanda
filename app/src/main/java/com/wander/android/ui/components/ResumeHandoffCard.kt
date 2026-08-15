package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroHandoffState

/**
 * "Continue on this device" — the one-tap pickup of a session another device is playing.
 *
 * Shown only while this device is idle, so it can never talk over local playback. The position is
 * carried through, so resuming lands exactly where the other device is rather than at the start.
 */
@Composable
internal fun ResumeHandoffCard(
    handoff: AgroHandoffState,
    deviceName: String,
    isResuming: Boolean,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Resolved on this device. Wander sends no `artworkUrl` — its cover URLs carry its own server
     * credentials, which have no business travelling through Agro — so reading it off the handoff
     * meant this card always drew the placeholder. Falls back to the handoff's own value for a
     * sender that does provide one.
     */
    artworkUrl: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Artwork(
                url = artworkUrl ?: handoff.artworkUrl,
                contentDescription = null,
                sizeDp = 48.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(48.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Continue from $deviceName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = handoff.trackTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = handoff.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FilledTonalButton(onClick = onResume, enabled = !isResuming) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isResuming) "Finding…" else "Resume",
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
            }
        }
    }
}
