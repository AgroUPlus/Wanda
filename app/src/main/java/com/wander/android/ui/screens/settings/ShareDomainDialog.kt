package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The domain the user's share links go out on.
 *
 * One field, because that is the whole setting: the page it points at decides everything else.
 * Clearing it and saving goes back to each backend's own link, which is why "Use default" is a
 * button rather than a switch somewhere else.
 */
@Composable
internal fun ShareDomainDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var domain by rememberSaveable(current) { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_share_links)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Share links go out on this domain instead of the backend's own, pointing at " +
                        "a page you host that forwards to the track."
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text(stringResource(R.string.settings_domain)) },
                    placeholder = { Text(stringResource(R.string.settings_share_example_com)) },
                    supportingText = { Text(stringResource(R.string.settings_links_become_https_domain_listen)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(domain) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            TextButton(onClick = { onSave("") }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_use_default)) }
        }
    )
}
