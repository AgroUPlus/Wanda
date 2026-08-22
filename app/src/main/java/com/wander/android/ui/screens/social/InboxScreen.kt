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
import androidx.compose.material3.CircularProgressIndicator
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
    var showSent by remember { mutableStateOf(false) }
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Inbox",
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

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SegmentedButton(
                    selected = !showSent,
                    onClick = { showSent = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(if (state.unread > 0) "Received (${state.unread})" else "Received")
                }
                SegmentedButton(
                    selected = showSent,
                    onClick = { showSent = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Sent")
                }
            }

            val drops = if (showSent) state.sent else state.received
            if (drops.isEmpty() && !state.loading) {
                EmptyState(
                    title = if (showSent) "Nothing sent yet" else "Nothing in inbox",
                    message = if (showSent) {
                        "Press and hold any track to send it to a friend with a note."
                    } else {
                        "When a friend sends you a track, it will appear here like a conversation."
                    }
                )
                return@Column
            }

            LazyColumn(
                contentPadding = contentPadding.listInset(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(count = drops.size, key = { index -> drops[index].id }) { index ->
                    val drop = drops[index]
                    ConversationDropMessage(
                        drop = drop,
                        incoming = !showSent,
                        isResolving = state.resolving == drop.id,
                        onPlay = { viewModel.play(drop) },
                        onArchive = { viewModel.archive(drop.id) }
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
    onArchive: () -> Unit
) {
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
            UserInitialsBadge(name = if (incoming) drop.fromUser else drop.toUser, isIncoming = incoming)
            Spacer(Modifier.width(6.dp))
            Text(
                text = userLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp).padding(4.dp),
                                strokeWidth = 2.5.dp
                            )
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
private fun UserInitialsBadge(name: String, isIncoming: Boolean) {
    com.wander.android.ui.components.CuteAvatar(
        seed = name,
        size = 20.dp
    )
}
