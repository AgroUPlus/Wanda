package com.wander.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.model.UnifiedTrack

/**
 * Pick a friend, optionally say why, send.
 *
 * The note comes before the friend list rather than after picking one: choosing a name is the last
 * act, so the list rows can be the send button and there is no second confirmation step. Handing
 * somebody a song should take one tap more than deciding to.
 *
 * Rows carry the friend's avatar. This is the one moment in the app where the thing being chosen
 * is a *person*, and a column of usernames is the least personal way to render that.
 */
@Composable
internal fun DropToFriendSheet(
    track: UnifiedTrack,
    onDismiss: () -> Unit,
    onSent: (String) -> Unit,
    viewModel: DropToFriendViewModel = hiltViewModel()
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Send “${track.title}”",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= MAX_NOTE_LENGTH) note = it },
                label = { Text("Say something (optional)") },
                supportingText = {
                    Text(
                        text = "${note.length} / $MAX_NOTE_LENGTH",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (note.length == MAX_NOTE_LENGTH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                maxLines = 4,
                enabled = !sending,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            if (friends.isEmpty()) {
                Text(
                    text = "You have no friends on this server yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = friends, key = { it.username }) { friend ->
                    ListItem(
                        headlineContent = { Text(friend.name) },
                        supportingContent = { Text("@" + friend.username) },
                        // A face, not just a name. Picking who to send a song to is the one
                        // moment in the app where you are choosing a *person*, and a list of
                        // usernames in a plain sheet is the least personal way to render that.
                        leadingContent = {
                            CuteAvatar(
                                seed = friend.username,
                                avatarUrl = friend.avatarUrl,
                                size = 40.dp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            // Disabled while a send is in flight, so a double tap cannot send the
                            // same track to two people by accident.
                            .clickable(enabled = !sending) {
                                viewModel.send(friend.username, track, note) { error ->
                                    onSent(
                                        if (error == null) {
                                            "Sent to ${friend.name}"
                                        } else {
                                            "That could not be sent"
                                        }
                                    )
                                    onDismiss()
                                }
                            }
                    )
                }
            }
        }
    }
}

private const val MAX_NOTE_LENGTH = 280
