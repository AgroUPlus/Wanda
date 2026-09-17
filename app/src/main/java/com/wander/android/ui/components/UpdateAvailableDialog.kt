package com.wander.android.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.core.update.UpdateCheckResult

/** Shown once per launch, only when the user opted into the auto-check in Settings > About. */
@Composable
internal fun UpdateAvailableDialog(
    update: UpdateCheckResult.UpdateAvailable,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_update_available)) },
        text = { Text(stringResource(R.string.common_wanda_available, update.version)) },
        confirmButton = {
            TextButton(onClick = {
                uriHandler.openUri(update.releaseUrl)
                onDismiss()
            },
                shapes = ButtonDefaults.shapes()
            ) { Text(stringResource(R.string.common_view_release)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_not_now)) }
        }
    )
}
