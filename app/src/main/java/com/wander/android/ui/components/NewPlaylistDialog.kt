package com.wander.android.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wander.android.R

/**
 * Asks for a playlist name.
 *
 * Shared by the add-to-playlist sheet and the Library tab's own "New playlist" action, which are
 * the two ways a playlist comes into existence.
 */
@Composable
fun NewPlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.common_name)) }
            )
        },
        confirmButton = {
            // Disabled rather than absent: the button is the thing the dialog is for, and hiding
            // it leaves no hint that a name is what is missing.
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.common_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
