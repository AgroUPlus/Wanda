package com.wander.android.ui.screens.social

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
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.ui.components.CuteAvatar
import com.wander.android.ui.components.PersonShape

/**
 * You, as one card at the top of the roster.
 *
 * It used to be a gradient banner with your face centred in it, which spent a third of the window
 * on the one person on this tab you already know. A card says the same thing — who you are, how many
 * people you have — in the space of a list row, and the QR button beside it is the other half of
 * "add a friend": the half for when they are standing next to you.
 */
@Composable
internal fun MyProfileCard(
    myUsername: String,
    myAvatarUrl: String?,
    friendCount: Int,
    onOpenMyProfile: () -> Unit,
    onShowCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpenMyProfile,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            CuteAvatar(
                seed = myUsername,
                avatarUrl = myAvatarUrl,
                size = 64.dp,
                shape = PersonShape
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (myUsername.isBlank()) stringResource(R.string.common_my_profile) else "@$myUsername",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(R.plurals.social_friend_count, friendCount, friendCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Surface(
                onClick = onShowCode,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = stringResource(R.string.social_show_friend_code),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
