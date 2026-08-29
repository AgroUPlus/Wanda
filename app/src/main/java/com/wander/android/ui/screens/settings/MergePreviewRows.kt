package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.KeptApartPair
import com.wander.android.data.repository.MergeGroup

@Composable
internal fun MergeGroupRow(group: MergeGroup, onKeepApart: (UnifiedTrack) -> Unit) {
    Surface(
        color = if (group.needsReview) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = group.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            group.renditions.forEach { rendition ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "· ${rendition.source.displayName} — ${format(rendition.durationMs)}" +
                            if (rendition.isLiked) "  ♥" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp)
                    )
                    IconButton(onClick = { onKeepApart(rendition) }) {
                        Icon(
                            imageVector = Icons.Rounded.CallSplit,
                            contentDescription = "Not the same recording as the rest of this group",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (group.needsReview) {
                Text(
                    text = reviewReason(group),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (group.combinedPlays > 0) {
                Text(
                    text = "${group.combinedPlays} plays would combine",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** One pinned pair, and the way back. */
@Composable
internal fun KeptApartRow(pair: KeptApartPair, onRejoin: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pair.a.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pair.a.source.displayName} — ${format(pair.a.durationMs)}  ·  " +
                        "${pair.b.source.displayName} — ${format(pair.b.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRejoin) {
                Icon(
                    imageVector = Icons.Rounded.Undo,
                    contentDescription = "Let these merge again",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** "1 pair", not "1 pairs". Omitted entirely when the user has pinned nothing. */
internal fun keptApartSummary(count: Int): String = when (count) {
    0 -> ""
    1 -> " · 1 pair kept apart"
    else -> " · $count pairs kept apart"
}

/** Says which signal tripped, so the row can be judged without opening anything. */
private fun reviewReason(group: MergeGroup): String = when {
    group.albums.size > 1 -> "Different albums: ${group.albums.joinToString(", ")}"
    else -> "Lengths differ by ${group.durationSpreadMs / 1000}s"
}

private fun format(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms % 60_000) / 1000)
