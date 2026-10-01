package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * The first thing a listener sees on the search screen, until they say yes: what is sent, to whom,
 * and one button that is the whole consent. Nothing is requested before it is pressed.
 */
@Composable
internal fun PodcastSearchConsent(onAllow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
        Text(stringResource(R.string.podcasts_search_consent_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.podcasts_search_consent_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onAllow, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.podcasts_search_consent_action))
        }
    }
}

/** PodcastIndex gives every app user their own free key; this takes it, and keeps it encrypted on the device. */
@Composable
internal fun PodcastSearchKeyForm(onSave: (key: String, secret: String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
        Text(stringResource(R.string.podcasts_search_keys_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.podcasts_search_keys_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            label = { Text(stringResource(R.string.podcasts_search_key)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            label = { Text(stringResource(R.string.podcasts_search_secret)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSave(key, secret) },
            enabled = key.isNotBlank() && secret.isNotBlank(),
            shapes = ButtonDefaults.shapes()
        ) { Text(stringResource(R.string.podcasts_search_keys_save)) }
    }
}
