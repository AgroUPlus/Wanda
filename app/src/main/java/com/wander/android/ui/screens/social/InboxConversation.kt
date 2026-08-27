package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.CuteAvatar
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
            ConversationDropMessage(
                drop = drop,
                incoming = incoming,
                // Your own messages are signed "You" and wear your own picture. They used to read
                // "You → theirname", which is the routing information of a mail client, not how a
                // person reads their own half of a conversation.
                senderName = if (incoming) state.nameOf(drop.fromUser) else "You",
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

/** A drop rendered as a conversation bubble with attached audio metadata. */
@Composable
private fun ConversationDropMessage(
    drop: AgroDrop,
    incoming: Boolean,
    senderName: String,
    avatarSeed: String,
    avatarUrl: String?,
    isResolving: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val bubbleShape = if (incoming) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    }

    val bubbleContainer = when {
        incoming && drop.isUnread -> MaterialTheme.colorScheme.primaryContainer
        incoming -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val bubbleContentColor = when {
        incoming && drop.isUnread -> MaterialTheme.colorScheme.onPrimaryContainer
        incoming -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        horizontalAlignment = if (incoming) Alignment.Start else Alignment.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            CuteAvatar(seed = avatarSeed, avatarUrl = avatarUrl, size = 20.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // A reaction travels back to the sender, unlike a read receipt — so their own
            // bubble is where they find out somebody answered. It sits on the recipient's own
            // bubble too, since the menu it is set from closes behind itself.
            if (drop.reaction != null) {
                Spacer(Modifier.width(6.dp))
                Text(text = drop.reaction, style = MaterialTheme.typography.labelLarge)
            }
            if (incoming && drop.isUnread) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }

        Surface(
            color = bubbleContainer,
            contentColor = bubbleContentColor,
            shape = bubbleShape,
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(top = 2.dp)
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = onPlay,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!drop.note.isNullOrBlank()) {
                    Text(
                        text = drop.note,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 10.dp, start = 2.dp, end = 2.dp)
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onPlay)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Artwork(
                            url = drop.artworkUrl,
                            contentDescription = drop.trackTitle,
                            sizeDp = 52.dp,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(52.dp)
                        )

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = drop.trackTitle,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = drop.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(6.dp))

                        if (isResolving) {
                            LoadingIndicator(modifier = Modifier.size(32.dp).padding(4.dp))
                        } else {
                            FilledIconButton(
                                onClick = onPlay,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
