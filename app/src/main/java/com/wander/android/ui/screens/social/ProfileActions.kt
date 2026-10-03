package com.wander.android.ui.screens.social

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.FriendState
import com.wander.android.ui.components.HeroActionButton
import com.wander.android.ui.components.HeroActionRow
import com.wander.android.ui.components.HeroActionTint

/**
 * What you came to this page to do with this person, as one wide button.
 *
 * With a friend that is asking them into a jam, with a narrower button beside it into Activity, where
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
    // The detail pages' own button row — see [HeroActionButton] — so a press here widens into its
    // neighbour and tightens its corners exactly as it does on an artist or album page. The row's
    // height includes its top padding, as theirs does.
    HeroActionRow(height = ProfileActionHeight + ProfileActionTopGap, modifier = Modifier.padding(top = ProfileActionTopGap)) {
        when (profile.friendState) {
            FriendState.ACCEPTED -> {
                WideButton(R.string.social_invite_to_jam, onInviteToJam, Icons.Rounded.GraphicEq, weight = InviteWeight)
                HeroActionButton(
                    onClick = onOpenActivity,
                    contentDescription = stringResource(R.string.common_activity),
                    icon = Icons.Rounded.AutoAwesome,
                    baseWeight = 1f,
                    iconSize = IconSize,
                    rowHeight = ProfileActionHeight,
                    tint = HeroActionTint.SECONDARY_CONTAINER
                )
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
    tonal: Boolean = false,
    weight: Float = 1f
) {
    HeroActionButton(
        onClick = onClick,
        contentDescription = null,
        icon = icon,
        baseWeight = weight,
        iconSize = IconSize,
        rowHeight = ProfileActionHeight,
        tint = if (tonal) HeroActionTint.SECONDARY_CONTAINER else HeroActionTint.PRIMARY_SOLID,
        label = stringResource(label)
    )
}

/** Shared with [ProfileSkeleton], which mirrors this row. */
internal val ProfileActionHeight = 56.dp
internal val ProfileActionTopGap = 24.dp
private val IconSize = 22.dp

/** Invite to jam beside the Activity button: about the split the old fixed 56 dp square left. */
private const val InviteWeight = 3.5f
