package com.wander.android.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Export and restore everything this device remembers.
 *
 * Self-contained on purpose — its own ViewModel, its own launchers, its own dialog — so it can be
 * dropped into a settings page as one row group without threading two more callbacks through
 * [SettingsActions], [SettingsHost] and [SettingsViewModel]. Nothing else in settings needs to know
 * this exists.
 */
@Composable
internal fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()

    // The passphrase is asked for *after* the file is chosen, so cancelling the picker never
    // prompts for a secret that is not going to be used.
    var pending by remember { mutableStateOf<PendingBackup?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri -> uri?.let { pending = PendingBackup(it, BackupPassphraseMode.EXPORT) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pending = PendingBackup(it, BackupPassphraseMode.IMPORT) } }

    pending?.let { request ->
        BackupPassphraseDialog(
            mode = request.mode,
            onConfirm = { passphrase ->
                when (request.mode) {
                    BackupPassphraseMode.EXPORT -> viewModel.export(request.uri, passphrase)
                    BackupPassphraseMode.IMPORT -> viewModel.import(request.uri, passphrase)
                }
                pending = null
            },
            onDismiss = { pending = null }
        )
    }

    Column {
        SettingsRow(
            title = "Export everything",
            subtitle = "Settings, customization and sign-ins, in one encrypted file you keep. " +
                "Protected by a passphrase you choose.",
            onClick = {
                viewModel.clearStatus()
                exportLauncher.launch(DEFAULT_FILE_NAME)
            },
            enabled = !busy
        )

        SettingsRow(
            title = "Import from a backup",
            subtitle = "Replaces the settings on this device with the ones in the file.",
            onClick = {
                viewModel.clearStatus()
                // Any type, not just JSON: a file that has been through a cloud drive or a chat app
                // frequently comes back as `application/octet-stream`, and filtering on the type we
                // wrote makes the user's own backup unselectable. The format is checked on read.
                importLauncher.launch(arrayOf("*/*"))
            },
            enabled = !busy
        )

        status?.let { Outcome(it) }
    }
}

@Composable
private fun Outcome(status: BackupStatus) {
    val (text, error) = when (status) {
        is BackupStatus.Exported ->
            "Backup written. Keep it somewhere you trust — it contains your sign-ins." to false

        is BackupStatus.Imported ->
            // Restarting is not advice, it is required: every setting is read into a `StateFlow`
            // when `SecureStorage` is constructed, and those were built before this file was
            // written. The app is showing the old values until the process restarts.
            "${status.settings} settings restored. Close and reopen Wanda to apply them." to false

        is BackupStatus.Failed -> status.message to true
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

private data class PendingBackup(val uri: Uri, val mode: BackupPassphraseMode)

private const val BACKUP_MIME = "application/json"
private const val DEFAULT_FILE_NAME = "wanda-backup.json"
