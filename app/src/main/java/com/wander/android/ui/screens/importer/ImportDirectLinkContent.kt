package com.wander.android.ui.screens.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp

@Composable
fun ImportDirectLinkContent(
    manualInput: String,
    isLoadingPlaylist: Boolean,
    error: String?,
    onInputChange: (String) -> Unit,
    onLoadPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Paste a link or text tracklist to inspect and choose tracks before importing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = manualInput,
            onValueChange = onInputChange,
            placeholder = { Text("https://open.spotify.com/playlist/...") },
            trailingIcon = {
                IconButton(onClick = { clipboardManager.getText()?.text?.let { onInputChange(it) } }) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                }
            },
            singleLine = false,
            maxLines = 4,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { err ->
            Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = onLoadPlaylist,
            enabled = manualInput.isNotBlank() && !isLoadingPlaylist,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes()
        ) {
            if (isLoadingPlaylist) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Reading...")
            } else {
                Text("Load Playlist")
            }
        }
    }
}
