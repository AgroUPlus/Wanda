package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Undo
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
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.data.repository.KeptApartPair
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
 *
 * Flagging a wrong merge without offering anything to do about it would leave the matcher's
 * judgement unappealable, so each rendition can be pinned apart from its group here. That write is
 * the one thing on this screen that touches the database, and it only ever keeps rows *separate*.
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
                                "${current.reviewable.size} worth checking" +
                                keptApartSummary(current.keptApart.size),
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
                MergeGroupRow(group, onKeepApart = { viewModel.keepApart(group, it) })
            }

            // A pinned pair is absent from every group above — that is what pinning does — so
            // without this the user could make a pin and then have no way to find or undo it.
            if (current.keptApart.isNotEmpty()) {
                item(key = "kept-apart-header") {
                    Text(
                        text = "Kept apart",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(current.keptApart, key = { "${it.a.id}|${it.b.id}" }) { pair ->
                    KeptApartRow(pair, onRejoin = { viewModel.rejoin(pair) })
                }
            }
        }
    }
}
