package com.wander.android.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R
import com.wander.android.data.sources.agro.AgroProfile
import com.wander.android.data.sources.agro.FriendState
import com.wander.android.ui.components.ConfirmRequest
import com.wander.android.ui.components.rememberConfirmState

/**
 * The two things you do to a person you rarely do: unfriend and block.
 *
 * Both used to sit as buttons under the name, at the same level as adding someone — an unfriend a
 * stray tap away from the thing you came to the page to do. In the overflow they are one deliberate
 * step further off, and each still asks first: both end something the other person has to agree to
 * again, and blocking also hides you from them. It sits at the trailing end of the page's
 * collapsing bar.
 */
@Composable
internal fun ProfileOverflow(profile: AgroProfile, onRemove: () -> Unit, onBlock: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val confirm = rememberConfirmState()
    val name = profile.displayName?.takeIf { it.isNotBlank() } ?: "@${profile.username}"
    val removeFriend = ConfirmRequest(
        title = stringResource(R.string.confirm_remove_friend_title, name),
        message = stringResource(R.string.confirm_remove_friend_message),
        confirmLabel = stringResource(R.string.social_remove_friend),
        onConfirm = onRemove
    )
    val block = ConfirmRequest(
        title = stringResource(R.string.confirm_block_title, name),
        message = stringResource(R.string.confirm_block_message),
        confirmLabel = stringResource(R.string.social_block),
        onConfirm = onBlock
    )

    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.background(
                if (open) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                CircleShape
            )
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.social_profile_more))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            offset = DpOffset(x = (-12).dp, y = 10.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            if (profile.friendState == FriendState.ACCEPTED) {
                MenuEntry(
                    label = stringResource(R.string.social_remove_friend),
                    icon = { Icon(Icons.Rounded.PersonRemove, null, Modifier.size(20.dp)) },
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        open = false
                        confirm.ask(removeFriend)
                    }
                )
            }
            MenuEntry(
                label = stringResource(R.string.social_block),
                icon = { Icon(Icons.Rounded.Block, null, Modifier.size(20.dp)) },
                iconColor = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.error,
                onClick = {
                    open = false
                    confirm.ask(block)
                }
            )
        }
    }
}

@Composable
private fun MenuEntry(
    label: String,
    icon: @Composable () -> Unit,
    iconColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
        },
        leadingIcon = icon,
        colors = MenuDefaults.itemColors(textColor = textColor, leadingIconColor = iconColor),
        contentPadding = PaddingValues(horizontal = 14.dp),
        onClick = onClick,
        // 200dp across with the menu's own 6dp inset on each side.
        modifier = Modifier.widthIn(min = 188.dp)
    )
}
