package com.wander.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rememberHaptics
import com.wander.android.ui.components.rememberPressScale

/**
 * The height of both dock controls, and the width of the square one.
 */
val DockControlSize = 56.dp

/** The margin around the dock row, applied equally on all four sides. */
private val DockInset = 12.dp

/**
 * Height of the dock's second row. Fixed, so the sheet above it can reserve exactly this much.
 */
val DockRowHeight = DockControlSize + DockInset * 2

/**
 * The dock's second row: one search field, and Friends beside it.
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
        DockSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onListen = onListen,
            onFocusChanged = { hasFocus ->
                focused = hasFocus
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
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource, label = "friendsPressScale")

    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "friendsScale"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
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
        onClick = {
            haptics.toggled(selected)
            onClick()
        },
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content
        ),
        modifier = Modifier
            .size(DockControlSize)
            .graphicsLayer {
                scaleX = scale * pressScale
                scaleY = scale * pressScale
            }
    ) {
        Icon(Icons.Rounded.People, contentDescription = "Friends")
    }
}
