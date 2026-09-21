package com.wander.android.ui.screens.welcome

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.ui.screens.settings.BackupPassphraseDialog
import com.wander.android.ui.screens.settings.BackupPassphraseMode
import com.wander.android.ui.screens.settings.BackupStatus
import com.wander.android.ui.screens.settings.BackupViewModel

/**
 * The escape hatch for someone who already has a `wanda-backup.json`, offered inline on
 * [WelcomeScreen]'s sources step: importing one replaces the "sign in again" dance above it with
 * whatever the backup already had configured. Reuses the same [BackupViewModel]/
 * [BackupPassphraseDialog] Settings' own backup screen uses — restoring is the same operation
 * regardless of where in the app it was started from.
 *
 * Optional and quiet on purpose: a fresh install with nothing to restore should never notice this
 * is here beyond the one line of text.
 */
@Composable
internal fun ImportBackupAction(
    onImported: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingUri = it } }

    pendingUri?.let { uri ->
        BackupPassphraseDialog(
            mode = BackupPassphraseMode.IMPORT,
            onConfirm = { passphrase ->
                viewModel.import(uri, passphrase)
                pendingUri = null
            },
            onDismiss = { pendingUri = null }
        )
    }

    // A successful import already restored whatever sign-ins the backup had; finishing setup here
    // is the same "done" the three cards above lead to, just reached a different way.
    LaunchedEffect(status) {
        if (status is BackupStatus.Imported) onImported()
    }

    Spacer(modifier = Modifier.height(4.dp))
    TextButton(
        onClick = {
            viewModel.clearStatus()
            importLauncher.launch(arrayOf("*/*"))
        },
        enabled = !busy,
        shapes = ButtonDefaults.shapes()
    ) {
        Text(stringResource(R.string.welcome_already_have_backup))
    }

    (status as? BackupStatus.Failed)?.let {
        Text(
            text = it.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
