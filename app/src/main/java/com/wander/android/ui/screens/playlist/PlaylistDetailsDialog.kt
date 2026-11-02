package com.wander.android.ui.screens.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * Renames a playlist and changes its description. The name may not be blank; an emptied
 * description clears the one the playlist had.
 */
@Composable
internal fun PlaylistDetailsDialog(
    name: String,
    description: String?,
    /** Shared through Agro, which keeps the title it was given. */
    isShared: Boolean,
    onSave: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by rememberSaveable { mutableStateOf(name) }
    var newDescription by rememberSaveable { mutableStateOf(description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_edit_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    label = { Text(stringResource(R.string.playlist_details_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newDescription,
                    onValueChange = { newDescription = it },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    label = { Text(stringResource(R.string.playlist_details_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isShared) {
                    Text(
                        text = stringResource(R.string.playlist_details_shared_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(newName, newDescription); onDismiss() },
                enabled = newName.isNotBlank(),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.playlist_details_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
