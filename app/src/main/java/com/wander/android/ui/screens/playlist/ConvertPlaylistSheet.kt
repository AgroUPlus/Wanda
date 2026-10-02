package com.wander.android.ui.screens.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * Explains what turning a backend's playlist into a Wanda playlist gives, before doing it.
 *
 * The original is left alone: the conversion is a copy, so nothing on the server changes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConvertPlaylistSheet(sourceName: String, onConvert: () -> Unit, onDismiss: () -> Unit) {
    // Opened in full: half-expanded, the buttons at the bottom would sit below the screen.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.playlist_convert_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.playlist_convert_body, sourceName),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.playlist_convert_intro),
                style = MaterialTheme.typography.bodyLarge
            )
            Benefit(Icons.Rounded.Shuffle, R.string.playlist_convert_mix, R.string.playlist_convert_mix_desc)
            Benefit(Icons.Rounded.Cloud, R.string.playlist_convert_agro, R.string.playlist_convert_agro_desc)
            Benefit(Icons.Rounded.IosShare, R.string.playlist_convert_share, R.string.playlist_convert_share_desc)
            Text(
                text = stringResource(R.string.playlist_convert_footnote, sourceName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(onClick = onConvert, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.playlist_convert_confirm))
                }
            }
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, @StringRes title: Int, @StringRes description: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
        )
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
