package com.wander.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The dock surface container when nothing is playing.
 *
 * Keeps the bottom of the app anchored as one cohesive block whether or not a track is active.
 */
@Composable
fun WanderDock(
    currentRoute: String?,
    query: String,
    onOpenLibrary: () -> Unit,
    onOpenFriends: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 6.dp,
        modifier = modifier.padding(horizontal = 12.dp)
    ) {
        WanderDockRow(
            currentRoute = currentRoute,
            query = query,
            onOpenLibrary = onOpenLibrary,
            onOpenFriends = onOpenFriends,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onListen = onListen
        )
    }
}
