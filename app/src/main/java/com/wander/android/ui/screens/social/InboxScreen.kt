package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.EmptyState
import com.wander.android.ui.components.headerInset
import com.wander.android.ui.components.listInset

/**
 * Songs friends handed you, and the ones you handed out, presented as a conversation feed.
 *
 * Drops are messages centered around music — styled as conversational speech bubbles with
 * rich attached playable track cards.
 */
@Composable
internal fun InboxScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit = {},
    viewModel: InboxViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(contentPadding.headerInset())
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    // Back closes the open thread first. Leaving the screen from inside one
                    // would make the list unreachable without opening the inbox again.
                    onClick = { if (state.openWith != null) viewModel.closeThread() else onBack() }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = state.openWith?.let { "@" + it } ?: "Messages",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                if (state.unread > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = if (state.unread > 99) "99+" else "${state.unread} new",
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            val openWith = state.openWith
            if (openWith == null) {
                if (state.threads.isEmpty() && !state.loading) {
                    EmptyState(
                        title = "No conversations yet",
                        message = "Press and hold any track to send it to a friend with a note."
                    )
                    return@Column
                }

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
                            friend = friend,
                            latest = latest,
                            sentByMe = state.isMine(latest),
                            unread = state.unreadByFriend[friend.lowercase()] ?: 0,
                            onClick = { viewModel.openThread(friend) }
                        )
                    }
                }
                return@Column
            }

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
                    ConversationDropMessage(
                        drop = drop,
                        incoming = !state.isMine(drop),
                        isResolving = state.resolving == drop.id,
                        onPlay = { viewModel.play(drop) },
                        onArchive = { viewModel.archive(drop.id) },
                        onReact = { emoji -> viewModel.react(drop, emoji) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * A drop rendered as a conversation bubble with attached audio metadata.
 */
@Composable
private fun ConversationDropMessage(
    drop: AgroDrop,
    incoming: Boolean,
    isResolving: Boolean,
    onPlay: () -> Unit,
    onArchive: () -> Unit,
    onReact: (String) -> Unit
) {
    // The picker opens on the bubble that was pressed, so it cannot be mistaken for a reaction to
    // the message above or below it.
    var picking by remember { mutableStateOf(false) }
    val bubbleAlignment = if (incoming) Alignment.Start else Alignment.End
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
        horizontalAlignment = bubbleAlignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Sender header line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            val userLabel = if (incoming) drop.fromUser else "You → ${drop.toUser}"
            UserInitialsBadge(name = if (incoming) drop.fromUser else drop.toUser, avatarUrl = null)
            Spacer(Modifier.width(6.dp))
            Text(
                text = userLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // A reaction travels back to the sender, unlike a read receipt — so their own
            // bubble is where they find out somebody answered.
            if (!incoming && drop.reaction != null) {
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

        // Chat Bubble
        Surface(
            color = bubbleContainer,
            contentColor = bubbleContentColor,
            shape = bubbleShape,
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(top = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Optional Note text inside bubble
                if (!drop.note.isNullOrBlank()) {
                    Text(
                        text = drop.note,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 10.dp, start = 2.dp, end = 2.dp)
                    )
                }

                // Attached Music Card
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

                // Reacting is the recipient's reply, so only an incoming bubble offers it. The
                // sender's own reaction to their own message would be a way to fake a response.
                if (incoming) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        if (picking) {
                            REACTIONS.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            onReact(emoji)
                                            picking = false
                                        }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = drop.reaction ?: "☺",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (drop.reaction == null) {
                                    bubbleContentColor.copy(alpha = 0.45f)
                                } else {
                                    bubbleContentColor
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { picking = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Footer actions
                if (incoming) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        IconButton(
                            onClick = onArchive,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Archive,
                                contentDescription = "Archive drop",
                                modifier = Modifier.size(16.dp),
                                tint = bubbleContentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserInitialsBadge(name: String, avatarUrl: String?) {
    com.wander.android.ui.components.CuteAvatar(
        seed = name,
        avatarUrl = avatarUrl,
        size = 20.dp
    )
}

/**
 * One row per person, showing the last thing exchanged with them.
 *
 * The list a mailbox never had. `Received` and `Sent` were two halves of the same exchange filed
 * apart, so a song and the song sent back in reply to it lived on different tabs and neither
 * screen could show a conversation.
 */
@Composable
private fun ThreadRow(
    friend: String,
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
        com.wander.android.ui.components.CuteAvatar(seed = friend, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "@" + friend,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal
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

/**
 * The six on offer.
 *
 * A fixed short row rather than the system emoji keyboard: this is a reaction, and picking one
 * should take a tap, not a search. They are the ones that mean something about a song someone
 * gave you — loved it, it goes hard, it made me cry, it made me laugh, I already know it, no.
 */
private val REACTIONS = listOf("❤️", "🔥", "😭", "😂", "👀", "🙃")
