package com.wander.android.ui.screens.settings

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.sources.agro.VaultAccess
import com.wander.android.data.sources.agro.VaultBackup
import com.wander.android.ui.components.GroupedCard
import com.wander.android.ui.components.ConfirmRequest
import com.wander.android.ui.components.rememberConfirmState
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * The Agro vault rows under Backup: the daily toggle, sign-ins in or out, back up now, and
 * restore. Hidden only when Agro is not paired at all. Otherwise, when backing up cannot work yet,
 * one row says why instead of the section quietly not being there — which read as a feature that
 * had never been built.
 */
@Composable
internal fun CloudBackupSection(onPair: () -> Unit, viewModel: CloudBackupViewModel = hiltViewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val unreachable by viewModel.unreachable.collectAsStateWithLifecycle()
    when (access) {
        VaultAccess.NOT_PAIRED -> return
        VaultAccess.READY -> VaultRows(viewModel)
        else -> Column {
            SettingsSection(stringResource(R.string.cloud_backup_section))
            GroupedCard(
                items = listOf<@Composable () -> Unit>({
                    SettingsRow(
                        title = stringResource(R.string.cloud_backup_auto),
                        subtitle = stringResource(
                            when {
                                access == null -> R.string.cloud_backup_checking
                                access == VaultAccess.NO_VAULT_KEY -> R.string.cloud_backup_no_key
                                unreachable -> R.string.cloud_backup_unreachable
                                else -> R.string.cloud_backup_server_lacks
                            }
                        ),
                        onClick = onPair.takeIf { access == VaultAccess.NO_VAULT_KEY },
                        icon = Icons.Rounded.CloudSync
                    )
                })
            )
        }
    }
}

@Composable
private fun VaultRows(viewModel: CloudBackupViewModel) {
    val auto by viewModel.auto.collectAsStateWithLifecycle()
    val accounts by viewModel.includeAccounts.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var restoring by remember { mutableStateOf(false) }
    val confirm = rememberConfirmState()
    val context = LocalContext.current
    val latest = backups?.firstOrNull()

    val includeAccounts = ConfirmRequest(
        title = stringResource(R.string.cloud_backup_accounts_confirm_title),
        message = stringResource(R.string.cloud_backup_accounts_confirm_message),
        confirmLabel = stringResource(R.string.cloud_backup_accounts_confirm),
        onConfirm = { viewModel.setIncludeAccounts(true) }
    )

    if (restoring) {
        RestoreFromAgroDialog(
            backups = backups.orEmpty(),
            onRestore = { id ->
                restoring = false
                viewModel.restore(id)
            },
            onDismiss = { restoring = false }
        )
    }

    Column {
        SettingsSection(stringResource(R.string.cloud_backup_section))
        GroupedCard(
            items = listOf<@Composable () -> Unit>(
                {
                    SettingsToggle(
                        title = stringResource(R.string.cloud_backup_auto),
                        subtitle = latest?.let { stringResource(R.string.cloud_backup_last, whenText(it.createdAt), it.deviceName.orEmpty()) }
                            ?: stringResource(R.string.cloud_backup_auto_sub),
                        checked = auto,
                        onCheckedChange = viewModel::setAuto,
                        icon = Icons.Rounded.CloudSync
                    )
                },
                {
                    SettingsRow(
                        title = stringResource(R.string.cloud_backup_now),
                        subtitle = latest?.let { stringResource(R.string.cloud_backup_size, Formatter.formatShortFileSize(context, it.sealedBytes)) }
                            ?: stringResource(R.string.cloud_backup_none),
                        onClick = viewModel::backUpNow,
                        enabled = !busy,
                        icon = Icons.Rounded.CloudUpload
                    )
                },
                {
                    SettingsToggle(
                        title = stringResource(R.string.cloud_backup_accounts),
                        subtitle = stringResource(R.string.cloud_backup_accounts_sub),
                        checked = accounts,
                        // Turning sign-ins on is the one choice here that widens what leaves the
                        // phone, so it is the one that asks first. Turning them off never does.
                        onCheckedChange = { on -> if (on) confirm.ask(includeAccounts) else viewModel.setIncludeAccounts(false) },
                        icon = Icons.Rounded.Key
                    )
                },
                {
                    SettingsRow(
                        title = stringResource(R.string.cloud_backup_restore),
                        subtitle = stringResource(R.string.cloud_backup_restore_sub),
                        onClick = {
                            viewModel.refresh()
                            restoring = true
                        },
                        enabled = !busy,
                        icon = Icons.Rounded.Restore
                    )
                }
            )
        )
        status?.let { BackupOutcome(it) }
    }
}

/** "3 hours ago", from the server's timestamp; shown as it came if it does not parse. */
internal fun whenText(iso: String): String = try {
    DateUtils.getRelativeTimeSpanString(
        OffsetDateTime.parse(iso).toInstant().toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS
    ).toString()
} catch (_: DateTimeParseException) {
    iso
}

/** The name a backup part goes by on screen. One this build does not know is shown as sent. */
@Composable
internal fun partName(section: String): String = when (section) {
    "SETTINGS" -> stringResource(R.string.cloud_backup_part_settings)
    "ACCOUNTS" -> stringResource(R.string.cloud_backup_part_accounts)
    "HISTORY" -> stringResource(R.string.cloud_backup_part_history)
    "LIBRARY" -> stringResource(R.string.cloud_backup_part_library)
    "MERGES" -> stringResource(R.string.cloud_backup_part_merges)
    "EPISODES" -> stringResource(R.string.cloud_backup_part_episodes)
    else -> section
}

/** A one-line description of what a backup holds. */
@Composable
internal fun partsOf(backup: VaultBackup): String =
    backup.sections.map { "${partName(it.name)} ${it.count}" }.joinToString(" · ")
