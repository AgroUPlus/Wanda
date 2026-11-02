package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.audio.fingerprint.EmbeddingModelManager

/**
 * The neural recognition model: downloaded on demand, not shipped in the app.
 *
 * Its own row rather than folded into the summary because it is a different kind of thing — a
 * one-time ~34 MB fetch that unlocks recognition, not a per-track measurement — and until it is
 * present that half of recognition simply is not there.
 */
@Composable
internal fun RecognitionModelRow(
    state: EmbeddingModelManager.State,
    embedded: Int,
    total: Int,
    onDownload: () -> Unit,
    onVerify: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.settings_recognition_model),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = when (state) {
                is EmbeddingModelManager.State.Downloading ->
                    stringResource(R.string.fp_model_downloading, (state.fraction * 100).toInt())
                is EmbeddingModelManager.State.Failed ->
                    state.message
                EmbeddingModelManager.State.Ready ->
                    pluralStringResource(R.plurals.fp_model_ready, total, embedded, total)
                EmbeddingModelManager.State.Absent ->
                    stringResource(R.string.fp_model_absent, EmbeddingModelManager.APPROX_MB)
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
                ) { Text(stringResource(R.string.settings_re_check_model)) }
            else -> FilledTonalButton(
                onClick = onDownload,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    stringResource(
                        if (state is EmbeddingModelManager.State.Failed) {
                            R.string.fp_model_retry_download
                        } else {
                            R.string.fp_model_download
                        }
                    )
                )
            }
        }
    }
}
