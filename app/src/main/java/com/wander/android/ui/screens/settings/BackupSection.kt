package com.wander.android.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.rememberShelfEntranceScale

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
        SettingsSection(stringResource(R.string.settings_section_backup))
        GroupedCard(
            items = listOf<@Composable () -> Unit>(
                {
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(0)),
                        title = stringResource(R.string.settings_export_everything),
                        subtitle = stringResource(R.string.settings_settings_customization_sign_ins_one),
                        onClick = {
                            viewModel.clearStatus()
                            exportLauncher.launch(DEFAULT_FILE_NAME)
                        },
                        enabled = !busy,
                        icon = Icons.Rounded.Upload
                    )
                },
                {
                    SettingsRow(
                        modifier = Modifier.scale(rememberShelfEntranceScale(1)),
                        title = stringResource(R.string.settings_import_from_backup),
                        subtitle = stringResource(R.string.settings_replaces_settings_device_ones_file),
                        onClick = {
                            viewModel.clearStatus()
                            // Any type, not just JSON: a file that has been through a cloud
                            // drive or a chat app frequently comes back as
                            // `application/octet-stream`, and filtering on the type we wrote
                            // makes the user's own backup unselectable. Checked on read.
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        enabled = !busy,
                        icon = Icons.Rounded.Download
                    )
                }
            )
        )

        status?.let { Outcome(it) }
    }
}

@Composable
private fun Outcome(status: BackupStatus) {
    val (text, error) = when (status) {
        is BackupStatus.Exported ->
            "Backup written. Keep it somewhere you trust — it contains your sign-ins." to false

        is BackupStatus.Imported -> {
            // Restarting is not advice, it is required: every setting is read into a `StateFlow`
            // when `SecureStorage` is constructed, and those were built before this file was
            // written. The app is showing the old values until the process restarts.
            val settings = pluralStringResource(
                R.plurals.backup_restored_settings,
                status.settings,
                status.settings
            )
            // Only mentioned when the file actually carried listening history. A backup written
            // before it travelled says nothing about it rather than claiming zero plays.
            val restored = if (status.plays > 0 || status.recaps > 0) {
                settings + " " + pluralStringResource(
                    R.plurals.backup_restored_history,
                    status.plays,
                    status.plays,
                    status.recaps
                )
            } else {
                settings
            }
            restored to false
        }

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
