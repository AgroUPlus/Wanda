package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wander.android.data.model.SearchKind

/**
 * Music / Videos / Podcasts.
 *
 * The three are mutually exclusive and cover the whole space of what a search can be, so they are
 * one connected toggle group, like every other single-choice switch in the app.
 */
@Composable
fun SearchKindToggle(
    selected: SearchKind,
    onSelect: (SearchKind) -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectedToggleGroup(
        options = SearchKind.entries,
        selected = selected,
        label = { it.label },
        onSelect = onSelect,
        modifier = modifier
    )
}
