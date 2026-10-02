package com.wander.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wander.android.R

/** Clears the queue after asking: a long queue built by hand is gone in one tap otherwise. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClearQueueButton(queueSize: Int, onClear: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    IconButton(onClick = { confirming = true }, shapes = IconButtonDefaults.shapes()) {
        Icon(
            imageVector = Icons.Rounded.DeleteSweep,
            contentDescription = stringResource(R.string.action_clear_queue),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_clear_queue_title),
            message = pluralStringResource(R.plurals.confirm_clear_queue_message, queueSize, queueSize),
            confirmLabel = stringResource(R.string.action_clear_queue),
            onConfirm = onClear,
            onDismiss = { confirming = false }
        )
    }
}
