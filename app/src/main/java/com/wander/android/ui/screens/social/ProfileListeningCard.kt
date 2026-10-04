package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.FriendState
import com.wander.android.ui.components.Artwork
import com.wander.android.ui.theme.extraColors

/**
 * What this person is playing, as one card: a heading and its answer used to be two separate
 * blocks of text, which made "nothing" look like a layout with a piece missing.
 *
 * Live, it is the cover and the track with a way to join in. Otherwise it is the reason there is
 * nothing to show — not friends yet, kept private, or simply not playing — because those are three
 * different answers and only one of them is "check back later".
 */
@Composable
internal fun ProfileListeningCard(
    profile: AgroProfile,
    now: AgroFriendNowPlaying?,
    isListeningAlong: Boolean,
    onListenAlong: () -> Unit,
    onStopListeningAlong: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(28.dp))
            .padding(12.dp)
    ) {
        if (now != null) {
            Artwork(
                url = now.artworkUrl,
                contentDescription = null,
                sizeDp = 56.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp)
            )
        } else {
            EmblemIcon(container = MaterialTheme.colorScheme.surfaceContainerHigh, size = 56.dp) {
                Icon(Icons.Rounded.MusicOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            ListeningLabel(live = now != null)
            if (now != null) {
                Text(
                    text = now.trackTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = now.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = idleReason(profile),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (now != null) {
            if (isListeningAlong) {
                RowActionButton(stringResource(R.string.social_stop_listening_short), onStopListeningAlong)
            } else {
                RowActionButton(stringResource(R.string.social_join), onListenAlong)
            }
        }
    }
}

@Composable
private fun ListeningLabel(live: Boolean) {
    val color: Color = if (live) MaterialTheme.extraColors.onLiveContainer else MaterialTheme.colorScheme.outline
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) Box(Modifier.size(6.dp).background(MaterialTheme.extraColors.liveDot, CircleShape))
        Text(
            text = stringResource(R.string.common_listening).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            ),
            color = color
        )
    }
}

@Composable
private fun idleReason(profile: AgroProfile): String = when {
    profile.friendState != FriendState.ACCEPTED -> stringResource(R.string.common_will_see_once_friends)
    !profile.showNowPlaying -> stringResource(R.string.social_keeps_listening_private, profile.name)
    else -> stringResource(R.string.social_nothing_playing_right_now)
}
