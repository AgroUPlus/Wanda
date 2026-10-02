package com.wander.android.ui.components

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wander.android.R

/**
 * Asked before anything that throws something away: a playlist, a downloaded file, a friend, a
 * session. Every such action is one tap from a row or a menu the user is otherwise just reading,
 * and none of them can be taken back by tapping again.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** A destructive action waiting on [ConfirmDialog]: what to ask, and what to do on a yes. */
class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit
)

@Composable
fun ConfirmDialog(request: ConfirmRequest, onDismiss: () -> Unit) = ConfirmDialog(
    title = request.title,
    message = request.message,
    confirmLabel = request.confirmLabel,
    onConfirm = request.onConfirm,
    onDismiss = onDismiss
)

/**
 * Holds at most one pending [ConfirmRequest] and shows its dialog, for a screen with several
 * destructive buttons: each one calls [ask] instead of acting.
 */
class ConfirmState internal constructor() {
    internal var pending by mutableStateOf<ConfirmRequest?>(null)

    fun ask(request: ConfirmRequest) {
        pending = request
    }
}

@Composable
fun rememberConfirmState(): ConfirmState {
    val state = remember { ConfirmState() }
    state.pending?.let { request -> ConfirmDialog(request, onDismiss = { state.pending = null }) }
    return state
}
