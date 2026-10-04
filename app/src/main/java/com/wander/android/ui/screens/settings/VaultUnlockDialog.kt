package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * Asks for the account passphrase that opens the vault key on a phone paired by QR.
 *
 * `remember`, not `rememberSaveable`, for the reason [BackupPassphraseDialog] gives: a passphrase
 * does not belong in a saved-state bundle, and retyping one field after a rotation is cheap.
 */
@Composable
internal fun VaultUnlockDialog(onUnlock: (String) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_backup_unlock_title)) },
        text = {
            Column {
                Text(stringResource(R.string.cloud_backup_unlock_message))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.cloud_backup_unlock_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onUnlock(passphrase) }, enabled = passphrase.isNotBlank()) {
                Text(stringResource(R.string.cloud_backup_unlock_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
