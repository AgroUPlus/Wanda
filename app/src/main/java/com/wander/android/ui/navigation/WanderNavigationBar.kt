package com.wander.android.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * The expressive short navigation bar: shorter than the classic one, with its own springy
 * selection animation supplied by the theme's motion scheme.
 *
 * @param onHeightChanged reports the laid-out height. This bar is shorter than the classic
 *   `NavigationBar` *and* grows with the system gesture inset, so the player sheet has to be told
 *   how tall it actually is rather than assuming a constant.
 */
@Composable
fun WanderNavigationBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    onHeightChanged: (Dp) -> Unit = {}
) {
    val density = LocalDensity.current
    ShortNavigationBar(
        modifier = modifier.onSizeChanged { size ->
            onHeightChanged(with(density) { size.height.toDp() })
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) }
            )
        }
    }
}
