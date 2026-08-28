package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "That other device has music you don't — want it?"
 *
 * Shaped like [ResumeHandoffCard] on purpose: both are the same kind of interruption, an offer
 * from another device that the user can take or dismiss, and two different-looking cards in the
 * same corner of the screen would read as two unrelated features.
 *
 * [sample] names a few tracks rather than only counting them. "412 tracks" is a chore; "Nirvana —
 * Lithium, and 411 more" is a decision the user can actually make.
 */
@Composable
internal fun SyncOfferCard(
    count: Int,
    sample: List<String>,
    isFetching: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) "1 track you don't have" else "$count tracks you don't have",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                if (sample.isNotEmpty()) {
                    Text(
                        text = sample.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FilledTonalButton(
                onClick = onAccept,
                enabled = !isFetching,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(if (isFetching) "Fetching…" else "Get them")
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
            }
        }
    }
}
