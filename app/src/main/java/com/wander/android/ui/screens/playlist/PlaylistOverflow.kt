package com.wander.android.ui.screens.playlist

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wander.android.R

/**
 * The playlist page's rarer actions, at the trailing end of its collapsing bar — the same menu
 * the profile page keeps there. A null action is one this playlist does not have; with none at
 * all the button is not shown.
 */
@Composable
internal fun PlaylistOverflow(onEditDetails: (() -> Unit)?) {
    if (onEditDetails == null) return
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier.background(
                if (open) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                CircleShape
            )
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.playlist_more_options))
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
            OverflowEntry(R.string.playlist_edit_details, Icons.Rounded.Edit) {
                open = false
                onEditDetails()
            }
        }
    }
}

@Composable
private fun OverflowEntry(@StringRes label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
        },
        leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
        onClick = onClick,
        modifier = Modifier.widthIn(min = 188.dp)
    )
}
