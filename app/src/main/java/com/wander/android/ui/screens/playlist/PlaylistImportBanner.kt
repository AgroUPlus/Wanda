package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.wander.android.R
import com.wander.android.core.work.ImportWorkState

/**
 * The playlist's import status above its tracks: how far matching has got while it runs, and what
 * to do about the tracks it could not find once it stops. Draws nothing for an ordinary playlist.
 */
@Composable
internal fun PlaylistImportBanner(
    work: ImportWorkState,
    notFoundCount: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        work.isRunning -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            if (work.total > 0) {
                LinearWavyProgressIndicator(
                    progress = { work.done.toFloat() / work.total },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = if (work.total > 0) {
                    stringResource(R.string.playlist_import_matching, work.done, work.total)
                } else {
                    stringResource(R.string.playlist_import_waiting)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        notFoundCount > 0 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp)
        ) {
            Text(
                text = pluralStringResource(R.plurals.playlist_import_not_found, notFoundCount, notFoundCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.playlist_import_retry))
            }
        }
    }
}
