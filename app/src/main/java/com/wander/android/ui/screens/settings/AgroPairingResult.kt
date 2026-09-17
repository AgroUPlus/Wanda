package com.wander.android.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroSignup

/** What a completed pairing looks like, in the words of the server that confirmed it. */
@Composable
internal fun AgroPairedMessage(state: AgroPairingState.Paired) {
    Text(
        text = stringResource(R.string.settings_signed_as, state.username),
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Your other devices will see this one as “${state.petname}”. " +
            "This device holds a token of its own, which you can revoke from the server without " +
            "changing your passphrase.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The one and only showing of a new account's passphrase.
 *
 * The server hashes it before answering and genuinely cannot produce it again, so this screen is
 * the whole recovery story. It is presented as something to copy now, not as a receipt.
 */
@Composable
internal fun AgroRegisteredMessage(signup: AgroSignup, server: String) {
    val context = LocalContext.current

    Text(
        text = stringResource(R.string.settings_now_exists, signup.username, server),
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = stringResource(R.string.settings_write_passphrase_down_server_stores) +
            "show it again — there is no reset.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = signup.passphrase,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
    TextButton(onClick = { context.copyPassphrase(signup.passphrase) }, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(R.string.settings_copy_passphrase))
    }

    if (signup.isPending) {
        Text(
            text = stringResource(R.string.settings_server_holds_new_accounts_until) +
                "sign in yet. Come back and pair once you have been approved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Copied without a label that names it, and marked sensitive so the system does not preview it.
 *
 * A passphrase in the clipboard is already more exposure than this project would choose; showing it
 * in a paste-preview toast on top of that is avoidable.
 */
private fun Context.copyPassphrase(passphrase: String) {
    val clip = ClipData.newPlainText("", passphrase).apply {
        description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
