package com.wander.android.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.listInset

/**
 * One row per person, showing the last thing exchanged with them.
 *
 * The list a mailbox never had. `Received` and `Sent` were two halves of the same exchange filed
 * apart, so a song and the song sent back in reply to it lived on different tabs and neither
 * screen could show a conversation.
 */
@Composable
internal fun InboxThreadList(
    state: InboxUiState,
    contentPadding: PaddingValues,
    onOpenThread: (String) -> Unit
) {
    LazyColumn(
        contentPadding = contentPadding.listInset(),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = state.threads.size,
            key = { index -> "thread_" + state.counterpart(state.threads[index]) }
        ) { index ->
            val latest = state.threads[index]
            val friend = state.counterpart(latest)
            ThreadRow(
                name = state.nameOf(friend),
                avatarUrl = state.avatarOf(friend),
                seed = friend,
                latest = latest,
                sentByMe = state.isMine(latest),
                unread = state.unreadByFriend[friend.lowercase()] ?: 0,
                onClick = { onOpenThread(friend) }
            )
        }
    }
}

@Composable
private fun ThreadRow(
    name: String,
    avatarUrl: String?,
    seed: String,
    latest: AgroDrop,
    sentByMe: Boolean,
    unread: Int,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Seeded on the username even when a picture exists, so the generated fallback stays the
        // same person's colours whether or not their avatar has loaded.
        CuteAvatar(seed = seed, avatarUrl = avatarUrl, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // Prefixed so a thread whose last message is your own does not read as though
                // they sent it — which, with only a title to go on, it otherwise would.
                text = (if (sentByMe) "You: " else "") + latest.trackTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (unread > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(if (unread > 99) "99+" else "$unread")
            }
        }
    }
}
