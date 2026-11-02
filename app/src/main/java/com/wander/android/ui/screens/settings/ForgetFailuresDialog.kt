package com.wander.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/**
 * What "Forget all credentials" could not finish. The credentials themselves are always erased;
 * this names the steps around them — the server unregister, a web sign-out, a cache — that did not.
 *
 * @param steps string resource ids, one naming each step that failed.
 */
@Composable
internal fun ForgetFailuresDialog(steps: List<Int>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forget_failed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.forget_failed_message))
                steps.forEach { step ->
                    Text(stringResource(R.string.forget_failed_step, stringResource(step)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
