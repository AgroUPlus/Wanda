package com.wander.android.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
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
        title = { Text("Update available") },
        text = { Text("Wanda ${update.version} is available.") },
        confirmButton = {
            TextButton(onClick = {
                uriHandler.openUri(update.releaseUrl)
                onDismiss()
            }) { Text("View release") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}
