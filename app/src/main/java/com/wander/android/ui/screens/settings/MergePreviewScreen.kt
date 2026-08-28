package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.repository.MergeGroup
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * What the recording migration would do to *this* library, before it does any of it.
 *
 * The migration folds renditions of one performance together and sums their play counts. That is a
 * one-way write over a year of listening, so it is worth seeing the answer against real music
 * rather than trusting that the matcher's fixtures covered the awkward cases. Nothing here writes.
 *
 * Ordered worst-first: groups whose lengths disagree, or whose rows name different albums, come
 * before the obvious merges. Those are where a live take or an alternate mix would be hiding.
 */
@Composable
internal fun MergePreviewScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: MergePreviewViewModel = hiltViewModel()
) {
    val report by viewModel.report.collectAsStateWithLifecycle()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding.headerInset())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Merge preview", style = MaterialTheme.typography.titleLarge)
        }

        val current = report
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Column
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "summary") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "${current.trackRows} rows would become " +
                                "${current.recordings} recordings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${current.merged} rows folded together · " +
                                "${current.splitLikes} likes currently split across copies · " +
                                "${current.reviewable.size} worth checking",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            text = "Nothing has been written. This is what the migration would do.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }

            items(current.groups, key = { it.renditions.first().id }) { group ->
                MergeGroupRow(group)
            }
        }
    }
}

@Composable
private fun MergeGroupRow(group: MergeGroup) {
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
                Text(
                    text = "· ${rendition.source.displayName} — ${format(rendition.durationMs)}" +
                        if (rendition.isLiked) "  ♥" else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

/** Says which signal tripped, so the row can be judged without opening anything. */
private fun reviewReason(group: MergeGroup): String = when {
    group.albums.size > 1 -> "Different albums: ${group.albums.joinToString(", ")}"
    else -> "Lengths differ by ${group.durationSpreadMs / 1000}s"
}

private fun format(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms % 60_000) / 1000)
