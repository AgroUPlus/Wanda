package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile

/**
 * One person in a list, with whatever action fits where you stand with them.
 *
 * The same row serves the friend list, the incoming and outgoing request lists, and search results,
 * because they differ only in that action — and writing four near-identical rows is how they drift
 * apart.
 */
@Composable
internal fun FriendRow(
    profile: AgroProfile,
    subtitle: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Avatar(profile, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * The line under a friend's name.
 *
 * Three cases, and the difference between the last two matters: a friend who is not listening is
 * simply idle, whereas one who keeps their playback private is telling you something about their
 * settings, not about their evening.
 */
internal fun friendSubtitle(profile: AgroProfile, nowPlaying: AgroFriendNowPlaying?): String = when {
    nowPlaying != null -> "${nowPlaying.trackTitle} · ${nowPlaying.artistName}"
    !profile.showNowPlaying -> "Keeps their listening private"
    else -> "Not listening right now"
}
