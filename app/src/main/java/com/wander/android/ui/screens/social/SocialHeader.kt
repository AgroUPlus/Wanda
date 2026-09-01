package com.wander.android.ui.screens.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.headerInset

/**
 * The top of the Friends tab.
 *
 * It used to be three identical tonal circles in a row — inbox, find people, my profile — which
 * said nothing about which was which, and made the one that was *you* look like just another
 * action. You lead the header instead, as your own face, the way every other person on this screen
 * is represented; the two things you do *to* the roster sit together at the other end.
 *
 * Your avatar is outside the `isPaired` branch on purpose. Your own profile — and the listening
 * statistics behind it — is about you, not about anyone else, so it must not disappear because no
 * server is paired or because nobody has been added yet. This is also where the stats button lives
 * now: it was in Home's header, which is the one place on the screen with nothing to do with
 * statistics.
 */
@Composable
internal fun SocialHeader(
    state: SocialUiState,
    unread: Int,
    contentPadding: PaddingValues,
    onOpenInbox: () -> Unit,
    onOpenMyProfile: () -> Unit,
    onOpenOffGrid: () -> Unit,
    onFindPeople: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(contentPadding.headerInset())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            CuteAvatar(
                seed = state.myUsername,
                avatarUrl = state.myAvatarUrl,
                size = 40.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenMyProfile)
                    .semantics { contentDescription = "My profile" }
            )
            Text(text = "Friends", style = MaterialTheme.typography.headlineLarge)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Outside the `isPaired` gate, unlike everything beside it. Off-grid sharing is the one
            // thing on this tab that works with no server at all, so hiding it until an account
            // exists would hide it from exactly the person it was built for.
            FilledTonalIconButton(onClick = onOpenOffGrid) {
                Icon(Icons.Rounded.Sensors, contentDescription = "Share off-grid")
            }
            if (state.isPaired) {
                InboxAction(unread = unread, onClick = onOpenInbox)
                FilledTonalIconButton(onClick = onFindPeople) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = "Find people")
                }
            }
        }
    }
}
