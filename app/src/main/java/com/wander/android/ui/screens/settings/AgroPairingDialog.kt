package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/** Where a manual pairing attempt currently stands, so the dialog can report rather than guess. */
sealed interface AgroPairingState {
    data object Idle : AgroPairingState
    data object Connecting : AgroPairingState
    data class Paired(val petname: String) : AgroPairingState
    data class Failed(val message: String) : AgroPairingState
}

/**
 * Manual entry for an Agro server. The pairing QR carries whatever address the dashboard was
 * serving itself on, which is wrong whenever that is `localhost` or an address the phone cannot
 * route to — a server behind a reverse proxy on its own domain is the normal case, not an edge one.
 */
@Composable
internal fun AgroPairingDialog(
    state: AgroPairingState,
    onPair: (server: String, username: String, passphrase: String) -> Unit,
    onDismiss: () -> Unit
) {
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    val connecting = state is AgroPairingState.Connecting

    AlertDialog(
        onDismissRequest = { if (!connecting) onDismiss() },
        title = { Text("Pair with Agro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server") },
                    placeholder = { Text("agro.example.com") },
                    supportingText = { Text("https:// is assumed. Use http:// for a plain LAN host.") },
                    singleLine = true,
                    enabled = !connecting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !connecting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    enabled = !connecting,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state is AgroPairingState.Failed) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPair(server, username, passphrase) },
                enabled = !connecting &&
                    server.isNotBlank() && username.isNotBlank() && passphrase.isNotBlank()
            ) {
                Text(if (connecting) "Connecting…" else "Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !connecting) { Text("Cancel") }
        }
    )
}
