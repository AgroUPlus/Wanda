package com.wander.android.ui.screens.social

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.FriendState

/**
 * What you came to this page to do with this person, as one wide button.
 *
 * With a friend that is asking them into a jam, with a squarer button beside it into Activity, where
 * the songs you two send each other land;
 * with anyone else it is whatever moves the friendship along. Unfriending and blocking live in the
 * top bar's overflow, not here, so nothing in this row ends anything.
 */
@Composable
internal fun ProfileActions(
    profile: AgroProfile,
    onInviteToJam: () -> Unit,
    onOpenActivity: () -> Unit,
    onAddFriend: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp)
    ) {
        when (profile.friendState) {
            FriendState.ACCEPTED -> {
                WideButton(R.string.social_invite_to_jam, onInviteToJam, Icons.Rounded.GraphicEq)
                Surface(
                    onClick = onOpenActivity,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(ActionHeight)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.common_activity))
                    }
                }
            }
            FriendState.NONE -> WideButton(R.string.social_add_friend, onAddFriend)
            FriendState.PENDING -> if (profile.outgoing) {
                WideButton(R.string.social_cancel_request, onDecline, tonal = true)
            } else {
                WideButton(R.string.common_accept, onAccept)
                WideButton(R.string.social_decline, onDecline, tonal = true)
            }
        }
    }
}

@Composable
private fun RowScope.WideButton(
    @StringRes label: Int,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    tonal: Boolean = false
) {
    val container: Color
    val content: Color
    if (tonal) {
        container = MaterialTheme.colorScheme.secondaryContainer
        content = MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        container = MaterialTheme.colorScheme.primary
        content = MaterialTheme.colorScheme.onPrimary
    }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = CircleShape,
        modifier = Modifier.weight(1f).height(ActionHeight)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(22.dp))
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
    }
}

private val ActionHeight = 56.dp
