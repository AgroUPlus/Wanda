package com.wander.android.ui.screens.settings

import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroAuthError
import com.wander.android.data.sources.agro.explain

/**
 * The fields for both modes.
 *
 * Signing in and signing up differ by one field each — a passphrase you already have, or an invite
 * code you may have been given — so they share a form rather than duplicating the server and
 * username entry, which behave identically in both.
 */
@Composable
internal fun AgroPairingForm(
    mode: AgroPairingMode,
    server: String,
    username: String,
    passphrase: String,
    inviteCode: String,
    enabled: Boolean,
    defaultServer: String,
    error: AgroAuthError?,
    onServerChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onModeChange: (AgroPairingMode) -> Unit
) {
    if (mode == AgroPairingMode.PAIR) {
        val context = LocalContext.current
        OutlinedButton(
            onClick = {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                runCatching {
                    context.startActivity(intent)
                }.onFailure {
                    runCatching {
                        context.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                    }.onFailure {
                        Toast.makeText(context, "Could not open camera app", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCode2,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Scan Pairing QR Code")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }

    OutlinedTextField(
        value = server,
        onValueChange = onServerChange,
        label = { Text("Server") },
        placeholder = { Text(defaultServer.substringAfter("://")) },
        supportingText = { Text("https:// is assumed. Use http:// for a plain LAN host.") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Username") },
        supportingText = if (mode == AgroPairingMode.CREATE) {
            { Text("Letters, digits, dots, dashes and underscores. Up to 32.") }
        } else {
            null
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )

    if (mode == AgroPairingMode.CREATE) {
        OutlinedTextField(
            value = inviteCode,
            onValueChange = onInviteCodeChange,
            label = { Text("Invite code (optional)") },
            supportingText = {
                Text("Some servers let anyone in and hold new accounts for approval. Others need a code.")
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Passphrase or Device Token") },
            supportingText = {
                Text("Account passphrase, or a Device Token / App Password from Agro Dashboard.")
            },
            singleLine = true,
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (error != null) {
        Text(
            text = error.explain(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    TextButton(
        onClick = {
            onModeChange(
                if (mode == AgroPairingMode.CREATE) AgroPairingMode.PAIR else AgroPairingMode.CREATE
            )
        },
        enabled = enabled,
        shapes = ButtonDefaults.shapes()
    ) {
        Text(
            if (mode == AgroPairingMode.CREATE) {
                "I already have an account"
            } else {
                "Create an account on this server"
            }
        )
    }
}
