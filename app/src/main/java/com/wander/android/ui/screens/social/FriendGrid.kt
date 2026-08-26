package com.wander.android.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.ListeningGreen

/**
 * Everyone, as faces.
 *
 * The Friends tab used to be a list of text rows whose subtitle, for most people most of the time,
 * read "Not listening right now" — a roster that says nothing, repeatedly. Avatars say who these
 * people are at a glance, and the green ring says which of them are listening without spending a
 * line of prose on the ones who are not.
 *
 * A row rather than a wrapping grid: it sits above the activity feed, and a grid tall enough to
 * hold twenty friends would push the only changing content on the screen below the fold.
 */
@Composable
internal fun FriendGrid(
    friends: List<AgroProfile>,
    listening: Set<String>,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(friends, key = { it.username }) { profile ->
            FriendAvatar(
                profile = profile,
                isListening = profile.username.lowercase() in listening,
                onClick = { onOpenProfile(profile.username) }
            )
        }
    }
}

@Composable
private fun FriendAvatar(profile: AgroProfile, isListening: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(AvatarColumnWidth)
    ) {
        Surface(
            shape = CircleShape,
            onClick = onClick,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                CuteAvatar(
                    seed = profile.username,
                    avatarUrl = profile.avatarUrl,
                    size = AvatarSize,
                    showBorder = isListening,
                    borderColor = ListeningGreen
                )
            }
        }
        Text(
            text = profile.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private val AvatarSize = 60.dp

/** Wide enough for a name that fits, narrow enough that four faces are on screen at once. */
private val AvatarColumnWidth = 76.dp
