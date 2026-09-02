package com.wander.android.ui.screens.settings

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.ui.components.TrackRow
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * What the fingerprint indexer has measured, track by track.
 *
 * The screen exists to answer one question — "why can't Wanda find this song when I hum it?" — so
 * it leads with what is *unmeasured* and what is being worked on, and leaves the finished majority
 * at the bottom where it is available but not in the way.
 */
@Composable
internal fun FingerprintsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    viewModel: FingerprintsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(contentPadding.headerInset())
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Fingerprints",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (state.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(24.dp))
            return
        }

        LazyColumn(
            contentPadding = contentPadding.listInset(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "summary") { Summary(state) }

            state.sections.forEach { section ->
                item(key = "header_${section.title}") {
                    SectionHeader(section, Modifier.padding(top = 20.dp))
                }
                items(section.rows, key = { it.track.id }) { row ->
                    TrackRow(
                        track = row.track,
                        onPlay = {},
                        // Not playable from here: this is a report, and a tap that started music
                        // while someone is auditing their index would be a surprise.
                        enabled = false,
                        fingerprintStatus = row.status
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(state: FingerprintsUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = "${state.indexed} of ${state.total} measured",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            // Both halves named, because a track can have one and not the other and that is
            // exactly the case where a search mysteriously fails.
            text = "A track counts as measured once it has both a recognition fingerprint and a " +
                "melody contour. Green is done, blue is being measured now, red is neither.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.indexed.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        state.processing?.let {
            Text(
                text = "Measuring \"${it.track.title}\" now",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(section: FingerprintSection, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            // The count belongs in the heading, not in a badge: "3 of 812" is the answer someone
            // opened this screen to get, per group.
            text = "${section.title} — ${section.indexed} of ${section.rows.size}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = section.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
