package com.wander.android.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * The height of both dock controls, and the width of the square one.
 *
 * One value for the pair: a search field and a button of different heights sitting side by side
 * read as two unrelated things that happen to be adjacent.
 */
val DockControlSize = 56.dp

/** The margin around the dock row, applied equally on all four sides. */
private val DockInset = 12.dp

/**
 * Height of the dock's second row. Fixed, so the sheet above it can reserve exactly this much.
 *
 * [DockControlSize] plus [DockInset] top and bottom — the row is inset from the card's edges by
 * the same amount on all four sides. It used to be 64dp around a 56dp field, which left 4dp above
 * and below against 12dp at the sides, and read as the search bar being pushed into the bottom
 * edge of the dock.
 */
val DockRowHeight = DockControlSize + DockInset * 2

/**
 * The dock's second row: one search field, and Friends beside it.
 *
 * There is no navigation bar. Library and Search were two of four tabs pointing at the same
 * question — "where is that song" — so they collapsed into the field that asks it: focusing the
 * field opens the library, typing turns the library into results from every source at once (see
 * `LibrarySurface`). Home is not a button because it is what you are already looking at; the back
 * gesture returns to it.
 *
 * The Friends button steps aside while the field has focus. Not decoration: the keyboard is up and
 * the query is the only thing that matters, and the field earns the width its results deserve.
 */
@Composable
fun WanderDockRow(
    currentRoute: String?,
    query: String,
    onOpenLibrary: () -> Unit,
    onOpenFriends: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onListen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Leaving the library gives the field up. Focus is what hides the Friends button, and focus
    // survives navigation — so going back to Home with the field still focused left the dock
    // permanently short one button, with no way to bring it back but tapping the field again.
    LaunchedEffect(currentRoute) {
        if (currentRoute != TopLevelDestination.LIBRARY.route) focusManager.clearFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DockInset),
        modifier = modifier
            .fillMaxWidth()
            .height(DockRowHeight)
            .padding(DockInset)
    ) {
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onListen = onListen,
            onFocusChanged = { hasFocus ->
                focused = hasFocus
                // Focus is the gesture that opens the library — not a separate tap, and not the
                // first keystroke either, so the destination is already there to type into.
                if (hasFocus) onOpenLibrary()
            },
            modifier = Modifier
                .weight(1f)
                .height(DockControlSize)
        )

        AnimatedVisibility(
            visible = !focused,
            enter = expandHorizontally(
                expandFrom = Alignment.Start,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ) + fadeIn() + scaleIn(initialScale = 0.7f),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.Start,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
            ) + fadeOut() + scaleOut(targetScale = 0.7f)
        ) {
            FriendsButton(
                selected = currentRoute == TopLevelDestination.FRIENDS.route,
                onClick = onOpenFriends
            )
        }
    }
}

@Composable
private fun FriendsButton(selected: Boolean, onClick: () -> Unit) {
    // The selected button is not merely tinted: it swells slightly, so the state reads at a glance
    // on a control that has no label under it to say which one you are on.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "friendsScale"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            // The same tone the search field wears, so the two read as a matched pair of controls
            // sitting on the dock rather than as a field with a stray button next to it.
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "friendsContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "friendsContent"
    )

    FilledIconButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content
        ),
        modifier = Modifier
            .size(DockControlSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Icon(Icons.Rounded.People, contentDescription = "Friends")
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onListen: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val searching = query.isNotEmpty()

    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search here", maxLines = 1) },
        leadingIcon = {
            // The note becomes a magnifier the moment the field has something to look for. The
            // idle state says "music"; the working state says what it is doing.
            //
            // While it is a note it is also a button: tapping it listens to the room and matches
            // what it hears against the library. Only while it is a note — once you are typing,
            // the icon is describing the search and pressing it would mean nothing.
            AnimatedContent(
                targetState = searching,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.6f))
                },
                label = "searchLeading"
            ) { active ->
                if (active) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                } else {
                    IconButton(onClick = onListen) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Identify what's playing"
                        )
                    }
                }
            }
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = searching,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f)
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results are already live by the time this fires — all it can usefully do is get the
        // keyboard out of the way of them.
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                onSearch()
            }
        ),
        shape = MaterialTheme.shapes.extraLarge,
        // A step *below* the dock card's own tone rather than above it. The dock is one card
        // holding two different things — the player and the search — and with the field only a
        // shade lighter than the card the boundary between them was invisible: the transport
        // controls looked like they belonged to the search bar. Sinking the field reads as a well
        // cut into the dock, which is the separation this needed.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = modifier.onFocusChanged { onFocusChanged(it.isFocused) }
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

