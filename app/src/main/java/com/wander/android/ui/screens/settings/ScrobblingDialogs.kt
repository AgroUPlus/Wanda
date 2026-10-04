package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** Pasting a ListenBrainz user token. Checked with ListenBrainz before it is kept. */
@Composable
internal fun ListenBrainzTokenDialog(
    busy: Boolean,
    error: String?,
    onOpenSettings: () -> Unit,
    onConnect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scrobbling_lb_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.scrobbling_lb_message))
                TextButton(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.scrobbling_lb_open))
                }
                SecretField(token, { token = it }, R.string.scrobbling_token)
                ErrorLine(error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(token) }, enabled = token.isNotBlank() && !busy, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.scrobbling_connect))
            }
        },
        dismissButton = { CancelButton(onDismiss) }
    )
}

/** The user's own Last.fm key, for a build that ships none. Kept on this phone with the sign-ins. */
@Composable
internal fun LastFmKeyDialog(onCreateKey: () -> Unit, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scrobbling_lfm_key_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.scrobbling_lfm_key_message))
                TextButton(onClick = onCreateKey, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.scrobbling_lfm_key_open))
                }
                SecretField(key, { key = it }, R.string.scrobbling_lfm_api_key)
                SecretField(secret, { secret = it }, R.string.scrobbling_lfm_api_secret)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key, secret) }, enabled = key.isNotBlank() && secret.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.scrobbling_lfm_continue))
            }
        },
        dismissButton = { CancelButton(onDismiss) }
    )
}

/** Waiting for the user to approve Wanda on Last.fm's own page, then finishing the sign-in. */
@Composable
internal fun LastFmApproveDialog(
    busy: Boolean,
    error: String?,
    onReopen: () -> Unit,
    onApproved: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scrobbling_lfm_approve_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.scrobbling_lfm_approve_message))
                TextButton(onClick = onReopen, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.scrobbling_lfm_reopen))
                }
                ErrorLine(error)
            }
        },
        confirmButton = {
            TextButton(onClick = onApproved, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.scrobbling_lfm_approved))
            }
        },
        dismissButton = { CancelButton(onDismiss) }
    )
}

@Composable
private fun SecretField(value: String, onValue: (String) -> Unit, label: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        label = { Text(stringResource(label)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ErrorLine(error: String?) {
    error?.let {
        Text(
            text = stringResource(R.string.scrobbling_failed, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun CancelButton(onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_cancel)) }
}
