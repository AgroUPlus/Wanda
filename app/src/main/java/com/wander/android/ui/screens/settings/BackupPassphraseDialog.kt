package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Asks for the passphrase that seals a backup.
 *
 * Deliberately **not** `rememberSaveable`: saved state is written to the instance bundle, which is
 * exactly the sort of place a passphrase must not be left lying. Losing what was typed on rotation
 * is the correct trade — it is one field, and the alternative is a secret in a system bundle.
 *
 * On export the field is confirmed twice, because a typo there is not discovered until the day the
 * backup is needed and cannot be opened. On import there is nothing to confirm against, and a
 * wrong passphrase announces itself immediately.
 */
@Composable
internal fun BackupPassphraseDialog(
    mode: BackupPassphraseMode,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    val confirming = mode == BackupPassphraseMode.EXPORT
    val mismatch = confirming && confirmation.isNotEmpty() && confirmation != passphrase
    val valid = passphrase.length >= MIN_LENGTH && (!confirming || confirmation == passphrase)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (confirming) "Choose a passphrase" else "Enter the passphrase")
        },
        text = {
            Column {
                Text(
                    text = if (confirming) {
                        "The backup holds your server passwords and sign-in tokens, so it is " +
                            "encrypted with this passphrase. Nobody can recover it for you — " +
                            "without it the file cannot be opened again."
                    } else {
                        "The passphrase this backup was created with."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 16.dp)
                )
                if (confirming) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Repeat passphrase") },
                        singleLine = true,
                        isError = mismatch,
                        supportingText = if (mismatch) {
                            { Text("The two do not match") }
                        } else {
                            null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = valid,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(if (confirming) "Export" else "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
        }
    )
}

internal enum class BackupPassphraseMode { EXPORT, IMPORT }

/**
 * Short, because this guards a file the user already has to possess — and because a rule strict
 * enough to be annoying is one people work around with "password1". Argon2id is what actually
 * makes a short passphrase expensive to guess.
 */
private const val MIN_LENGTH = 6
