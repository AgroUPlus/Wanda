package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.ListeningGreen
import com.wander.android.ui.components.scrollingTitle

/**
 * One friend and what they are playing, for the row across the top of the Friends tab.
 *
 * Only ever built for friends who *are* playing something. A friend with nothing showing is either
 * not listening or has that switched off, and neither is a card worth a slot in a horizontal row
 * the user has to scroll.
 */
@Composable
internal fun FriendPresenceCard(
    profile: AgroProfile,
    nowPlaying: AgroFriendNowPlaying,
    isListeningAlong: Boolean,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpenProfile,
        colors = CardDefaults.cardColors(
            containerColor = if (isListeningAlong) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = modifier.width(200.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(profile, size = 28.dp)
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f).scrollingTitle()
                )
                if (nowPlaying.isPlaying) {
                    Surface(
                        color = ListeningGreen,
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A sealed session travelled through the server as ciphertext. The lock says so
                // whether or not it opened — that it was encrypted is true either way, and it is
                // the same badge the drops inbox uses for the same fact.
                if (nowPlaying.encryptedPresence != null) {
                    EncryptedThreadLock()
                }
                Text(
                    // A session that arrived sealed and would not open: this device knows a friend
                    // is playing but not what. Saying so is honest; showing the server's
                    // placeholder as though it were a title would not be.
                    text = if (nowPlaying.isLocked) "Private session" else nowPlaying.trackTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nowPlaying.isLocked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f).scrollingTitle()
                )
            }
            if (!nowPlaying.isLocked) {
                Text(
                    text = nowPlaying.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.scrollingTitle()
                )
            }
        }
    }
}

/**
 * A friend's picture, or a deterministic cute procedural avatar.
 */
@Composable
internal fun Avatar(profile: AgroProfile, size: androidx.compose.ui.unit.Dp) {
    com.wander.android.ui.components.CuteAvatar(
        seed = profile.username,
        avatarUrl = profile.avatarUrl,
        size = size
    )
}
