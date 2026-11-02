package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.ui.components.listInset

/** One exchange, oldest first, as speech bubbles with a playable track attached to each. */
@Composable
internal fun InboxConversation(
    state: InboxUiState,
    contentPadding: PaddingValues,
    onPlay: (AgroDrop) -> Unit,
    onReact: (AgroDrop, String) -> Unit,
    onRemove: (String) -> Unit
) {
    // Held here rather than per bubble: only one menu can be open, and keying it to the drop id
    // means the sheet survives the list recomposing underneath it.
    var acting by remember { mutableStateOf<String?>(null) }

    // Where the thread stopped being readable by the server, if it ever was. Computed once per
    // conversation rather than per bubble: it is a property of the list, not of a message.
    val boundaryId = remember(state.conversation) { encryptionBoundaryId(state.conversation) }

    LazyColumn(
        contentPadding = contentPadding.listInset(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(
            count = state.conversation.size,
            key = { index -> state.conversation[index].id }
        ) { index ->
            val drop = state.conversation[index]
            val incoming = !state.isMine(drop)
            if (drop.id == boundaryId) EncryptionBoundary()
            ConversationDropMessage(
                drop = drop,
                incoming = incoming,
                // Your own messages are signed "You" and wear your own picture. They used to read
                // "You → theirname", which is the routing information of a mail client, not how a
                // person reads their own half of a conversation.
                senderName = if (incoming) state.nameOf(drop.fromUser) else stringResource(R.string.inbox_sender_you),
                avatarSeed = if (incoming) drop.fromUser else state.me,
                avatarUrl = if (incoming) state.avatarOf(drop.fromUser) else state.myAvatarUrl,
                isResolving = state.resolving == drop.id,
                onPlay = { onPlay(drop) },
                onLongPress = { acting = drop.id }
            )
        }
    }

    val target = acting?.let { id -> state.conversation.firstOrNull { it.id == id } }
    if (target != null) {
        DropActionsSheet(
            currentReaction = target.reaction,
            canReact = !state.isMine(target),
            onReact = { emoji ->
                onReact(target, emoji)
                acting = null
            },
            onRemove = {
                onRemove(target.id)
                acting = null
            },
            onDismiss = { acting = null }
        )
    }
}
