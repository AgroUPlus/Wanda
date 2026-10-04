package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.sources.agro.VaultAccess
import com.wander.android.ui.components.GroupedCard

/**
 * Says so when this phone has no vault key, and so sends what it plays to Agro readable.
 *
 * Without the key the handoff cannot be sealed, and it used to go out in the clear with nothing
 * anywhere saying so — a phone paired by QR looked exactly as private as one paired by passphrase.
 * Shown only while that is true; unlocking here is the same passphrase prompt the backup rows use.
 */
@Composable
internal fun UnencryptedPresenceNotice(onPair: () -> Unit, viewModel: CloudBackupViewModel = hiltViewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var unlocking by remember { mutableStateOf(false) }

    if (access != VaultAccess.NO_VAULT_KEY && access != VaultAccess.KEYLESS_SERVER_TOO_OLD) return

    if (unlocking) {
        VaultUnlockDialog(
            onUnlock = { passphrase ->
                unlocking = false
                viewModel.unlock(passphrase)
            },
            onDismiss = { unlocking = false }
        )
    }

    Column {
        GroupedCard(
            items = listOf<@Composable () -> Unit>({
                SettingsRow(
                    title = stringResource(R.string.settings_presence_unencrypted),
                    subtitle = stringResource(
                        when {
                            busy -> R.string.cloud_backup_unlocking
                            access == VaultAccess.NO_VAULT_KEY -> R.string.settings_presence_unencrypted_sub
                            else -> R.string.settings_presence_unencrypted_old_server
                        }
                    ),
                    onClick = if (access == VaultAccess.NO_VAULT_KEY) ({ unlocking = true }) else onPair,
                    enabled = !busy,
                    destructive = true,
                    icon = Icons.Rounded.LockOpen
                )
            })
        )
        status?.let { BackupOutcome(it) }
    }
}
