package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.FriendState
import com.wander.android.ui.components.ConfirmRequest
import com.wander.android.ui.components.rememberConfirmState

/**
 * Befriend, accept, decline, unfriend or block. Removing a friend and blocking ask first: both
 * end something the other person has to agree to again, and blocking also hides you from them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileActions(profile: AgroProfile, viewModel: ProfileViewModel) {
    val confirm = rememberConfirmState()
    val name = profile.displayName?.takeIf { it.isNotBlank() } ?: "@${profile.username}"
    val removeFriend = ConfirmRequest(
        title = stringResource(R.string.confirm_remove_friend_title, name),
        message = stringResource(R.string.confirm_remove_friend_message),
        confirmLabel = stringResource(R.string.social_remove_friend),
        onConfirm = viewModel::remove
    )
    val block = ConfirmRequest(
        title = stringResource(R.string.confirm_block_title, name),
        message = stringResource(R.string.confirm_block_message),
        confirmLabel = stringResource(R.string.social_block),
        onConfirm = viewModel::block
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        when (profile.friendState) {
            FriendState.NONE -> Button(onClick = viewModel::sendRequest, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.social_add_friend))
            }
            FriendState.PENDING -> if (profile.outgoing) {
                OutlinedButton(onClick = viewModel::remove, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.social_cancel_request)) }
            } else {
                Button(onClick = viewModel::accept, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.common_accept)) }
                OutlinedButton(onClick = viewModel::remove, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.social_decline)) }
            }
            FriendState.ACCEPTED -> OutlinedButton(onClick = { confirm.ask(removeFriend) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.social_remove_friend))
            }
        }
        TextButton(onClick = { confirm.ask(block) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.social_block)) }
    }
}
