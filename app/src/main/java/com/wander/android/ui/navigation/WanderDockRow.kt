package com.wander.android.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Height of the dock's second row. Fixed, so the sheet above it can reserve exactly this much. */
val DockRowHeight = 64.dp

/**
 * The dock's second row: the four destinations, or the search field.
 *
 * One row that swaps rather than two rows stacked, because the alternative is a bottom chrome that
 * grows taller on one screen than on every other — and the dock's whole point is that it is a
 * single fixed block. Navigation is never taken away: the field replaces the destinations only on
 * the Search screen, which you can always leave with the system back gesture.
 *
 * The destinations keep [ShortNavigationBarItem] rather than becoming hand-rolled buttons, so the
 * expressive selection spring and the accessibility semantics of a navigation bar survive being
 * moved into a card.
 */
@Composable
fun WanderDockRow(
    currentRoute: String?,
    query: String,
    onNavigate: (TopLevelDestination) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onSearchScreen = currentRoute == TopLevelDestination.SEARCH.route

    AnimatedContent(
        targetState = onSearchScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "dockRow",
        modifier = modifier
            .fillMaxWidth()
            .height(DockRowHeight)
    ) { searching ->
        if (searching) {
            SearchField(query = query, onQueryChange = onQueryChange, onSearch = onSearch)
        } else {
            Destinations(currentRoute = currentRoute, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun Destinations(currentRoute: String?, onNavigate: (TopLevelDestination) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            Box(modifier = Modifier.weight(1f)) {
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
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search every source") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = MaterialTheme.shapes.extraLarge,
        // The dock already paints a container; the field's own would draw a second box inside it.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

/**
 * The dock when nothing is playing.
 *
 * The same card the player sheet draws while docked, minus the player — so the bottom of the app
 * is one block whether or not there is a track, instead of the card appearing and disappearing
 * under the user.
 */
@Composable
fun WanderDock(
    currentRoute: String?,
    query: String,
    onNavigate: (TopLevelDestination) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
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
            onNavigate = onNavigate,
            onQueryChange = onQueryChange,
            onSearch = onSearch
        )
    }
}
