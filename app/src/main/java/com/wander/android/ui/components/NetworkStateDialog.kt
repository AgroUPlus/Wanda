package com.wander.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.ui.NetworkPrompt

/**
 * Offered when the network state changes, not when a setting is opened.
 *
 * Offline mode is only useful if it is on at the moment the network is gone, and the moment the
 * network is gone is exactly when the user is least likely to go looking for a settings toggle.
 * The reverse matters just as much: an offline mode left on after the signal came back quietly
 * hides three quarters of the library, and looks like the app has lost it.
 */
@Composable
fun NetworkStateDialog(
    prompt: NetworkPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val goingOffline = prompt == NetworkPrompt.GO_OFFLINE

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (goingOffline) Icons.Rounded.CloudOff else Icons.Rounded.CloudQueue,
                contentDescription = null
            )
        },
        title = {
            Text(stringResource(if (goingOffline) R.string.network_offline_title else R.string.network_online_title))
        },
        text = {
            Text(
                stringResource(if (goingOffline) R.string.network_offline_message else R.string.network_online_message)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (goingOffline) R.string.network_go_offline else R.string.network_go_online))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.common_not_now))
            }
        }
    )
}
