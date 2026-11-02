package com.wander.android.ui.screens.social

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.WandaSheet
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.FriendState

/**
 * Finding people by username.
 *
 * The server matches on a prefix and lists only accounts that opted into being discoverable, so an
 * empty result is the normal answer for most of what anyone types. The sheet says so rather than
 * looking broken — "no such user" and "that user chose not to be listed" are the same answer here,
 * deliberately, and neither is an error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserSearchSheet(
    state: UserSearchState,
    onQueryChange: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onToggleCode: () -> Unit,
    onRefreshCode: () -> Unit,
    onRevokeCode: () -> Unit,
    onDismiss: () -> Unit
) {
    WandaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_find_people), style = MaterialTheme.typography.titleLarge)
                // The other way in, for two people in the same room — where searching by
                // username needs one of them to be publicly listed, and being added once is not
                // a reason to be listed forever.
                TextButton(onClick = onToggleCode, shapes = ButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(stringResource(if (state.showingCode) R.string.social_hide_my_code else R.string.social_my_code))
                }
            }

            FriendCodePanel(
                visible = state.showingCode,
                code = state.friendCode,
                error = state.friendCodeError,
                onRefresh = onRefreshCode,
                onRevoke = onRevokeCode
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.common_username)) },
                supportingText = { Text(stringResource(R.string.social_starts_need_most_name_not)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )

            state.error?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.isSearching) {
                LoadingIndicator(modifier = Modifier.padding(vertical = 16.dp))
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.results, key = { "search_" + it.username }) { profile ->
                    FriendRow(
                        profile = profile,
                        subtitle = "@" + profile.username,
                        actionLabel = stringResource(profile.actionLabel(state.requested)),
                        onAction = { onSendRequest(profile.username) }
                            .takeIf { profile.friendState == FriendState.NONE &&
                                profile.username !in state.requested },
                        onClick = { onOpenProfile(profile.username) }
                    )
                }
            }

            if (state.query.isNotBlank() && state.results.isEmpty() && !state.isSearching) {
                Text(
                    text = stringResource(R.string.social_nobody_name_listed_people_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@StringRes
private fun AgroProfile.actionLabel(requested: Set<String>): Int = when {
    username in requested -> R.string.social_search_asked
    friendState == FriendState.ACCEPTED -> R.string.social_search_friends
    friendState == FriendState.PENDING -> R.string.social_search_pending
    else -> R.string.social_search_add
}
