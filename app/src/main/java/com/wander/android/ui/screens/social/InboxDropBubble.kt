package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroDrop
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.rememberHaptics

/** A drop rendered as a conversation bubble with attached audio metadata. */
@Composable
internal fun ConversationDropMessage(
    drop: AgroDrop,
    incoming: Boolean,
    senderName: String,
    avatarSeed: String,
    avatarUrl: String?,
    isResolving: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit
) {
    val haptics = rememberHaptics()
    val encryptedLabel = stringResource(R.string.inbox_end_to_end_encrypted)
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
            // Sealed notes only. The absence of a padlock on the older half of a conversation is
            // itself the information, and `EncryptionBoundary` above already says what it means —
            // an open or crossed-out padlock would say the same thing a second time, louder.
            //
            // No `contentDescription`: the spoken description belongs to the bubble as a whole,
            // and a padlock announced between a name and a message turns one sentence into three.
            if (drop.isEncrypted) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(12.dp)
                )
            }
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
                // What the silent padlock above means, for a reader who cannot see it. On the
                // bubble rather than on the icon, so it is announced once, with the message.
                .semantics {
                    if (drop.isEncrypted) stateDescription = encryptedLabel
                }
                .clip(bubbleShape)
                .combinedClickable(
                    onClick = onPlay,
                    onLongClickLabel = stringResource(R.string.common_more_options),
                    onLongClick = {
                        haptics.heldDown()
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

                DropTrackCard(drop, isResolving, onPlay)
            }
        }
    }
}

/** The track a drop carries: cover, title and artist, and a play button that shows when it is resolving. */
@Composable
private fun DropTrackCard(drop: AgroDrop, isResolving: Boolean, onPlay: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
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
                shape = MaterialTheme.shapes.extraSmall,
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
                        contentDescription = stringResource(R.string.action_play),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
