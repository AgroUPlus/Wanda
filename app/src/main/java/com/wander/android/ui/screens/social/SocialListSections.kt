package com.wander.android.ui.screens.social

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroFriendNowPlaying
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape
import com.wander.android.ui.components.SegmentGap
import com.wander.android.ui.components.segmentedShape
import com.wander.android.ui.theme.extraColors

/**
 * Everyone, as one grouped list, with the ones playing something saying what.
 *
 * Live friends get a Join button right on the row: the carousel above shows what they are playing,
 * and this is where you act on it without opening their page first.
 */
internal fun LazyListScope.allFriendsSection(
    friends: List<AgroProfile>,
    playing: (String) -> AgroFriendNowPlaying?,
    isListeningAlong: (String) -> Boolean,
    onOpenProfile: (String) -> Unit,
    onJoin: (String) -> Unit
) {
    item(key = "friends_header") { SectionTitle(stringResource(R.string.social_all_friends)) }
    items(count = friends.size, key = { "friend_" + friends[it].username }) { index ->
        val profile = friends[index]
        val now = playing(profile.username)
        GroupedListItem(
            shape = segmentedShape(index, friends.size),
            headline = profile.name,
            supporting = now?.let { statusLine(it) } ?: stringResource(R.string.social_not_listening),
            supportingColor = if (now != null) MaterialTheme.extraColors.onLiveContainer else MaterialTheme.colorScheme.outline,
            onClick = { onOpenProfile(profile.username) },
            leading = { FriendFace(profile) },
            trailing = {
                if (now != null && !isListeningAlong(profile.username)) {
                    RowActionButton(stringResource(R.string.social_join)) { onJoin(profile.username) }
                } else {
                    ListChevron()
                }
            },
            modifier = Modifier.groupedRow(index)
        )
    }
}

/** Requests in either direction: the same grouped strip, with the one action that fits. */
internal fun LazyListScope.requestSection(
    key: String,
    @StringRes title: Int,
    profiles: List<AgroProfile>,
    @StringRes actionLabel: Int,
    onAction: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    if (profiles.isEmpty()) return
    item(key = key + "_header") { SectionTitle(stringResource(title)) }
    items(count = profiles.size, key = { key + "_" + profiles[it].username }) { index ->
        val profile = profiles[index]
        GroupedListItem(
            shape = segmentedShape(index, profiles.size),
            headline = profile.name,
            supporting = "@" + profile.username,
            onClick = { onOpenProfile(profile.username) },
            leading = { FriendFace(profile) },
            trailing = { RowActionButton(stringResource(actionLabel)) { onAction(profile.username) } },
            modifier = Modifier.groupedRow(index)
        )
    }
}

/** The way into Activity, which says how much is waiting there when anything is. */
internal fun LazyListScope.activityRow(unread: Int, onOpenActivity: () -> Unit) {
    item(key = "activity") {
        GroupedListItem(
            shape = segmentedShape(0, 1),
            headline = stringResource(R.string.common_activity),
            supporting = if (unread > 0) {
                pluralStringResource(R.plurals.social_unread_count, unread, unread)
            } else {
                stringResource(R.string.social_activity_subtitle)
            },
            onClick = onOpenActivity,
            leading = {
                EmblemIcon(container = MaterialTheme.colorScheme.tertiaryContainer) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
        )
    }
}

@Composable
private fun FriendFace(profile: AgroProfile) {
    CuteAvatar(seed = profile.username, avatarUrl = profile.avatarUrl, size = 48.dp, shape = PersonShape)
}

@Composable
private fun statusLine(now: AgroFriendNowPlaying): String =
    stringResource(R.string.social_track_by_artist, now.trackTitle, now.artistName)

private fun Modifier.groupedRow(index: Int): Modifier =
    padding(start = 16.dp, end = 16.dp, top = if (index == 0) 0.dp else SegmentGap)
