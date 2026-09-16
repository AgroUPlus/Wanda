package com.wander.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.repository.StorageBreakdown

/**
 * A source-by-source breakdown of what's downloaded, plus one-tap cleanup of downloads nobody has
 * touched in [com.wander.android.data.repository.StorageButlerRules.STALE_AFTER_DAYS] days.
 *
 * Sits alongside [playbackStorageTab]'s existing "Clear streaming cache" row rather than
 * replacing it — that row already owns the streaming cache; this owns explicit downloads, which
 * nothing in Settings reported on before.
 */
@Composable
internal fun StorageButlerSection(
    modifier: Modifier = Modifier,
    viewModel: StorageButlerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val breakdown = state.breakdown ?: return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (breakdown.downloadsBySource.isNotEmpty()) {
            StorageBar(breakdown)
            Spacer(Modifier.height(8.dp))
            breakdown.downloadsBySource.forEach { entry ->
                Text(
                    text = stringResource(
                        R.string.storage_source_line,
                        entry.source.displayName,
                        entry.trackCount,
                        formatBytes(entry.bytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (breakdown.staleDownloadCount > 0) {
            Spacer(Modifier.height(12.dp))
            SettingsRow(
                title = stringResource(R.string.storage_clean_stale_title),
                subtitle = stringResource(
                    R.string.storage_clean_stale_subtitle,
                    breakdown.staleDownloadCount,
                    formatBytes(breakdown.staleDownloadBytes)
                ),
                onClick = viewModel::cleanStaleDownloads,
                enabled = !state.isCleaning
            )
        }
    }
}

/** A single stacked bar, one colour per source, sized by its share of total downloaded bytes. */
@Composable
private fun StorageBar(breakdown: StorageBreakdown) {
    val total = breakdown.downloadsBySource.sumOf { it.bytes }.coerceAtLeast(1L)
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
    ) {
        breakdown.downloadsBySource.forEachIndexed { index, entry ->
            val weight = (entry.bytes.toFloat() / total).coerceAtLeast(0.02f)
            Row(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxWidth()
                    .background(paletteColor(palette, index))
            ) {}
        }
    }
}

private fun paletteColor(palette: List<Color>, index: Int): Color = palette[index % palette.size]
