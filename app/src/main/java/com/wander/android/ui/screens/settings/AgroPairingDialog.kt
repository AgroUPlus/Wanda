package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroAuthError

/** Whether the dialog is signing in to an existing account or making a new one. */
internal enum class AgroPairingMode { PAIR, CREATE }

/**
 * Manual entry for an Agro server, and signup for a server that accepts strangers.
 *
 * Manual entry exists because the pairing QR carries whatever address the dashboard was serving
 * itself on, which is wrong whenever that is `localhost` or an address the phone cannot route to —
 * a server behind a reverse proxy on its own domain is the normal case, not an edge one.
 *
 * The dialog no longer closes itself on success. It used to, on the reasoning that the row behind
 * it reported the connection; but that row reports the same thing whether the token works or not,
 * so a successful pairing produced no confirmation anywhere. Now it says so, and the user closes it.
 */
@Composable
internal fun AgroPairingDialog(
    state: AgroPairingState,
    defaultServer: String,
    onPair: (server: String, username: String, passphrase: String) -> Unit,
    onSignUp: (server: String, username: String, inviteCode: String) -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(AgroPairingMode.PAIR) }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var inviteCode by rememberSaveable { mutableStateOf("") }

    val busy = state is AgroPairingState.Connecting
    val resolvedServer = server.ifBlank { defaultServer }
    // A rate limit is the one failure where trying again immediately is guaranteed to fail too.
    val throttled = (state as? AgroPairingState.Failed)?.error is AgroAuthError.RateLimited

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                when {
                    state is AgroPairingState.Paired -> "Connected"
                    state is AgroPairingState.Registered -> "Account created"
                    mode == AgroPairingMode.CREATE -> "Create an Agro account"
                    else -> "Pair with Agro"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is AgroPairingState.Paired -> AgroPairedMessage(state)
                    is AgroPairingState.Registered -> AgroRegisteredMessage(
                        signup = state.signup,
                        server = resolvedServer
                    )

                    else -> AgroPairingForm(
                        mode = mode,
                        server = server,
                        username = username,
                        passphrase = passphrase,
                        inviteCode = inviteCode,
                        enabled = !busy,
                        defaultServer = defaultServer,
                        error = (state as? AgroPairingState.Failed)?.error,
                        onServerChange = { server = it },
                        onUsernameChange = { username = it },
                        onPassphraseChange = { passphrase = it },
                        onInviteCodeChange = { inviteCode = it },
                        onModeChange = { mode = it }
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is AgroPairingState.Paired -> TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Done") }

                is AgroPairingState.Registered -> TextButton(
                    onClick = {
                        // The passphrase was just minted here, so pairing needs nothing typed.
                        passphrase = state.signup.passphrase
                        username = state.signup.username
                        onPair(resolvedServer, state.signup.username, state.signup.passphrase)
                    },
                    // A queued account cannot log in yet; offering the button would only produce
                    // an error the previous screen already explained.
                    enabled = !state.signup.isPending,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(if (state.signup.isPending) "Waiting for approval" else "Pair this device")
                }

                else -> TextButton(
                    onClick = {
                        if (mode == AgroPairingMode.CREATE) {
                            onSignUp(resolvedServer, username, inviteCode)
                        } else {
                            onPair(resolvedServer, username, passphrase)
                        }
                    },
                    enabled = !busy && !throttled && username.isNotBlank() &&
                        (mode == AgroPairingMode.CREATE || passphrase.isNotBlank()),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        when {
                            busy -> "Connecting…"
                            mode == AgroPairingMode.CREATE -> "Create account"
                            else -> "Pair"
                        }
                    )
                }
            }
        },
        dismissButton = {
            // "Check again" is the only useful move while an account waits for approval: nothing
            // about what was typed is wrong, so re-submitting the form would be theatre.
            if ((state as? AgroPairingState.Failed)?.error is AgroAuthError.NotActive) {
                TextButton(onClick = onRecheck, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text("Check again") }
            } else {
                TextButton(onClick = onDismiss, enabled = !busy, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            }
        }
    )
}
