package com.wander.android.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.VaultBackup

/**
 * Choosing which backup on Agro to restore. Nothing is restored until one is picked and confirmed,
 * and the newest is picked to begin with — the one a phone starting over almost always wants.
 */
@Composable
internal fun RestoreFromAgroDialog(backups: List<VaultBackup>, onRestore: (String) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember(backups) { mutableStateOf(backups.firstOrNull()?.id) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cloud_backup_restore)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                if (backups.isEmpty()) {
                    Text(stringResource(R.string.cloud_backup_restore_empty))
                    return@Column
                }
                Text(
                    text = stringResource(R.string.cloud_backup_restore_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                backups.forEach { backup ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = chosen == backup.id, onClick = { chosen = backup.id }, role = Role.RadioButton)
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = chosen == backup.id, onClick = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = listOfNotNull(whenText(backup.createdAt), backup.deviceName).joinToString(" · "),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = partsOf(backup),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.cloud_backup_size, Formatter.formatShortFileSize(context, backup.sealedBytes)),
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
                onClick = { chosen?.let(onRestore) },
                enabled = chosen != null,
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.cloud_backup_restore_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
