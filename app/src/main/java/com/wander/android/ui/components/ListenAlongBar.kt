package com.wander.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.data.repository.ListenAlongSession
import com.wander.android.data.repository.ResolvedFrom
import com.wander.android.data.sources.agro.Jam

/**
 * The banner shown while following a friend, above the mini-player.
 *
 * It exists to answer three questions the session could not previously answer anywhere in the UI:
 * who you are following, **what is actually playing on this device**, and what happened when the
 * host played something this device cannot find.
 *
 * The source matters and is deliberately not hidden. A YouTube Music match is the right song *by
 * name* — it may be a live take, a remaster, a different edit — so someone hearing a version that
 * sounds wrong can see why instead of assuming the feature is broken.
 */
/**
 * Its total height, so the screens underneath can reserve the space.
 *
 * Two text rows and the padding around them. Stated as a constant rather than measured because the
 * inset has to be known before the bar is laid out.
 */
val ListenAlongBarHeight: Dp = 64.dp

@Composable
internal fun ListenAlongBar(
    session: ListenAlongSession,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        // The mini-player's shape and footprint, so this reads as the player having been taken
        // over rather than as a banner stacked on top of one.
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Listening along with ${session.host}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = session.statusLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.scrollingTitle()
            )
        }
        TextButton(onClick = onLeave, shapes = ButtonDefaults.shapes()) { Text("Leave") }
    }
}

/**
 * One line describing the state of the session, in the user's terms.
 *
 * The unresolvable case comes first because it is the one that otherwise looks like a bug: the
 * host changed track, nothing happened here, and without this the app said nothing at all about
 * why.
 */
private fun ListenAlongSession.statusLine(): String {
    unresolvable?.let { return "Can't find “$it” in any of your sources" }
    val track = nowPlaying ?: return "Waiting for them to play something"
    val where = when (resolvedFrom) {
        ResolvedFrom.LOCAL_STORAGE -> "from local storage"
        ResolvedFrom.NAVIDROME -> "streamed from Navidrome"
        ResolvedFrom.YOUTUBE_MUSIC -> "matched on YouTube Music"
        ResolvedFrom.P2P_DIRECT -> "streamed over Wi-Fi (P2P)"
        ResolvedFrom.AGRO_RELAY -> "streamed via Agro relay"
        null -> "finding it…"
    }
    return "${track.trackTitle} — ${track.artistName} · $where"
}

val JamBarHeight: Dp = 44.dp

@Composable
internal fun JamBar(
    jam: Jam,
    onOpenJam: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpenJam)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Live indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                            shape = CircleShape
                        )
                )
                Text(
                    text = "Jam",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                // Live profile pictures of people in the room (small)
                AvatarGroup(
                    usernames = jam.members,
                    size = 22.dp,
                    overlap = 6.dp,
                    maxDisplay = 4
                )
                Text(
                    text = jam.nowPlaying?.title ?: "Queue is empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f, fill = false).scrollingTitle()
                )
            }
            androidx.compose.material3.IconButton(
                onClick = onLeave,
                modifier = Modifier.size(24.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Close,
                    contentDescription = "Leave Jam",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
