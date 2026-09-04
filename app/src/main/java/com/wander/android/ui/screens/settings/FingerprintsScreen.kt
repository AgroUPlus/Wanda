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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.core.audio.fingerprint.EmbeddingModelManager
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
                style = MaterialTheme.typography.headlineLarge,
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
            item(key = "summary") { Summary(state, viewModel::setPaused) }
            item(key = "model") {
                RecognitionModelRow(
                    state.recognitionModel,
                    embedded = state.embedded,
                    total = state.total,
                    onDownload = viewModel::downloadRecognitionModel,
                    onVerify = viewModel::verifyRecognitionModel
                )
            }

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

/**
 * The neural recognition model: downloaded on demand, not shipped in the app.
 *
 * Its own row rather than folded into the summary because it is a different kind of thing — a
 * one-time ~34 MB fetch that unlocks recognition, not a per-track measurement — and until it is
 * present that half of recognition simply is not there.
 */
@Composable
private fun RecognitionModelRow(
    state: EmbeddingModelManager.State,
    embedded: Int,
    total: Int,
    onDownload: () -> Unit,
    onVerify: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "Recognition model",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = when (state) {
                is EmbeddingModelManager.State.Downloading ->
                    "Downloading… ${(state.fraction * 100).toInt()}%"
                is EmbeddingModelManager.State.Failed ->
                    state.message
                EmbeddingModelManager.State.Ready ->
                    "Downloaded and checked. $embedded of $total songs have a neural fingerprint."
                EmbeddingModelManager.State.Absent ->
                    "Not downloaded. The recogniser needs a one-time ${EmbeddingModelManager.APPROX_MB} MB model."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        when (state) {
            is EmbeddingModelManager.State.Downloading ->
                LinearWavyProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            EmbeddingModelManager.State.Ready ->
                TextButton(
                    onClick = onVerify,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text("Re-check model") }
            else -> FilledTonalButton(
                onClick = onDownload,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(if (state is EmbeddingModelManager.State.Failed) "Retry download" else "Download model")
            }
        }
    }
}

@Composable
private fun Summary(state: FingerprintsUiState, onPausedChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = "${state.embedded} of ${state.total} recognisable",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            // Recognition now runs on the neural fingerprint, not the landmark index. The rows
            // below still show landmark/contour state during the changeover; green there is a
            // track the old path also covers.
            text = "A song is recognisable once it has a neural fingerprint (see Recognition " +
                "model above). Green below is done, blue is being measured now, red is neither.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (state.total > 0) {
            // Wavy while there is a track being measured, flat when there is not.
            //
            // The wave is not decoration here — it is the difference between "this is moving" and
            // "this is where it stopped". A determinate bar at 40% looks identical whether the
            // indexer is working or was killed an hour ago, and that ambiguity is the whole
            // question someone opens this screen with.
            LinearWavyProgressIndicator(
                progress = { state.embedded.toFloat() / state.total },
                // Flat the moment measuring is paused, without waiting for the run to unwind. A
                // wave still rolling under a button that says "Resume" is the screen contradicting
                // itself about the one thing it is here to report.
                amplitude = { if (state.processing != null && !state.isPaused) 1f else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
        // A button rather than a switch, because this is an action taken on something happening
        // right now — "stop that" — not a preference that describes how the app should behave.
        FilledTonalButton(
            onClick = { onPausedChange(!state.isPaused) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(if (state.isPaused) "Resume measuring" else "Pause measuring")
        }

        if (state.isPaused) {
            Text(
                text = "Paused. Nothing is being measured until you resume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        state.processing?.takeIf { !state.isPaused }?.let {
            Text(
                text = "Measuring \"${it.track.title}\" now",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
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
