package com.wander.android.ui.screens.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.audio.fingerprint.EmbeddingModelManager

/** The second step: the optional neural recognition model download. */
@Composable
internal fun RecognitionStep(
    model: EmbeddingModelManager.State,
    onDownload: () -> Unit
) {
    Text(text = stringResource(R.string.welcome_song_recognition), style = MaterialTheme.typography.headlineLarge)
    Text(
        text = stringResource(R.string.welcome_hold_phone_speaker_wanda_names),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = "It needs a one-time ${EmbeddingModelManager.APPROX_MB} MB model. " +
                "After that it works fully offline.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    when (model) {
        is EmbeddingModelManager.State.Downloading -> {
            val fraction by animateFloatAsState(model.fraction, label = "modelDownload")
            Text(
                "Downloading… ${(model.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        EmbeddingModelManager.State.Ready ->
            Text(
                "Downloaded and checked — recognition is ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        is EmbeddingModelManager.State.Failed -> {
            Text(
                model.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            FilledTonalButton(
                onClick = onDownload,
                modifier = Modifier.padding(top = 8.dp),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.common_try_again)) }
        }
        EmbeddingModelManager.State.Absent ->
            FilledTonalButton(
                onClick = onDownload,
                modifier = Modifier.padding(top = 4.dp),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.welcome_download_model)) }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.welcome_can_skip_download_later_from),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
