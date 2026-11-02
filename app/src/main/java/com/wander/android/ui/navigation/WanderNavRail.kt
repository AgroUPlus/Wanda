package com.wander.android.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.wander.android.R

/**
 * The navigation rail that stands in for the dock's buttons on a wide window — a tablet, an
 * unfolded foldable.
 *
 * It carries every [DockItem], not just the few the user picked for the dock: the dock caps them
 * so the search field stays wide enough to type in, and a rail has no field to squeeze. Home gets
 * an entry too, since a phone reaches it with back and a rail is where a wide layout looks for it.
 *
 * The items scroll and stop [bottomClearance] short of the bottom, because the docked mini player
 * spans the window's width and floats over the rail's lower end.
 */
@Composable
fun WanderNavRail(
    currentRoute: String?,
    bottomClearance: Dp,
    onOpenHome: () -> Unit,
    onOpenItem: (DockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            RailItem(
                icon = Icons.Outlined.Home,
                label = R.string.nav_home,
                selected = currentRoute == TopLevelDestination.HOME.route,
                onClick = onOpenHome
            )
            DockItem.entries.forEach { item ->
                RailItem(
                    icon = item.icon,
                    label = item.label,
                    selected = currentRoute == item.route,
                    onClick = { onOpenItem(item) }
                )
            }
            Spacer(Modifier.height(bottomClearance))
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, @StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label)) }
    )
}
