package com.wander.android.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wander.android.R

/**
 * A load that failed, said as such, with a way to try again.
 *
 * Shown in place of, or above, what a list would hold, so a failure never passes for "nothing
 * here". [message] is a sentence from `userMessageRes`, never an exception's own text.
 */
@Composable
fun LoadErrorState(
    @StringRes message: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        title = stringResource(R.string.error_load_title),
        message = stringResource(message),
        icon = Icons.Rounded.CloudOff,
        actionLabel = stringResource(R.string.error_retry),
        onAction = onRetry,
        modifier = modifier
    )
}
