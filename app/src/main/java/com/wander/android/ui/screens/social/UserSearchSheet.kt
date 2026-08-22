package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
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
            Text(text = "Find people", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Username") },
                supportingText = { Text("Starts with — you need most of the name, not a fragment.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )

            state.error?.let { message ->
                Text(
                    text = message,
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
                        actionLabel = profile.actionLabel(state.requested),
                        onAction = { onSendRequest(profile.username) }
                            .takeIf { profile.friendState == FriendState.NONE &&
                                profile.username !in state.requested },
                        onClick = { onOpenProfile(profile.username) }
                    )
                }
            }

            if (state.query.isNotBlank() && state.results.isEmpty() && !state.isSearching) {
                Text(
                    text = "Nobody by that name is listed. People only appear here if they have " +
                        "made themselves discoverable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

private fun AgroProfile.actionLabel(requested: Set<String>): String = when {
    username in requested -> "Asked"
    friendState == FriendState.ACCEPTED -> "Friends"
    friendState == FriendState.PENDING -> "Pending"
    else -> "Add"
}
