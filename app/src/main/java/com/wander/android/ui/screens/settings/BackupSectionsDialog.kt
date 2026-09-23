package com.wander.android.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.core.backup.BackupSection

/**
 * Which parts go into the backup. Everything is ticked to start with — a backup that silently
 * leaves something behind is the failure this exists to prevent — and at least one must stay on.
 */
@Composable
internal fun BackupSectionsDialog(
    onConfirm: (Set<BackupSection>) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf(BackupSection.entries.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_choose_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_choose_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BackupSection.entries.forEach { section ->
                    val checked = section in chosen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = checked, role = Role.Checkbox) {
                                chosen = if (it) chosen + section else chosen - section
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(stringResource(section.label), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(section.detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(chosen) },
                enabled = chosen.isNotEmpty(),
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.backup_choose_next)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@get:StringRes
private val BackupSection.label: Int
    get() = when (this) {
        BackupSection.SETTINGS -> R.string.backup_section_settings
        BackupSection.ACCOUNTS -> R.string.backup_section_accounts
        BackupSection.HISTORY -> R.string.backup_section_history
        BackupSection.LIBRARY -> R.string.backup_section_library
        BackupSection.MERGES -> R.string.backup_section_merges
        BackupSection.EPISODES -> R.string.backup_section_episodes
    }

@get:StringRes
private val BackupSection.detail: Int
    get() = when (this) {
        BackupSection.SETTINGS -> R.string.backup_section_settings_detail
        BackupSection.ACCOUNTS -> R.string.backup_section_accounts_detail
        BackupSection.HISTORY -> R.string.backup_section_history_detail
        BackupSection.LIBRARY -> R.string.backup_section_library_detail
        BackupSection.MERGES -> R.string.backup_section_merges_detail
        BackupSection.EPISODES -> R.string.backup_section_episodes_detail
    }
