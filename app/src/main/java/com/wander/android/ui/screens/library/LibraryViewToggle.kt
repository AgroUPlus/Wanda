package com.wander.android.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.R

private val ToggleSize = 40.dp

/**
 * List or grid, as two connected icon toggles: round on the outside, tight where they meet, the
 * chosen one filled. Icons alone — the pair sits at the end of a toolbar that has a sort label
 * and a filter chip in it already.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LibraryViewToggle(
    selected: LibraryView,
    onSelect: (LibraryView) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier
    ) {
        LibraryView.entries.forEachIndexed { index, view ->
            ToggleButton(
                checked = view == selected,
                onCheckedChange = { onSelect(view) },
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(ToggleSize)
                    .semantics { role = Role.RadioButton }
            ) {
                Icon(
                    imageVector = view.icon,
                    contentDescription = stringResource(view.label),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val LibraryView.icon: ImageVector
    get() = when (this) {
        LibraryView.LIST -> Icons.AutoMirrored.Rounded.ViewList
        LibraryView.GRID -> Icons.Rounded.GridView
    }

@get:StringRes
private val LibraryView.label: Int
    get() = when (this) {
        LibraryView.LIST -> R.string.library_view_list
        LibraryView.GRID -> R.string.library_view_grid
    }
