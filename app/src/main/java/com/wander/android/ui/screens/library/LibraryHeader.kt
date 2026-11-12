package com.wander.android.ui.screens.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** The Library's title, with search and the way into History where Home keeps Settings. */
@Composable
internal fun LibraryTitleRow(onOpenSearch: () -> Unit, onOpenHistory: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_library),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f)
        )
        // The dock's own search, opened over the Library: there is one search, not a second one
        // that only knows this screen.
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.library_open_search))
        }
        // History is a log rather than a collection, so it reads better as one thing you can go
        // and look at than as another tab competing with them.
        IconButton(onClick = onOpenHistory) {
            Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.library_listening_history))
        }
    }
}

/** A section chip's resting corners: tight, so the chosen one's full pill stands out. */
private val ChipRestingShape = RoundedCornerShape(8.dp)

/**
 * Every section that is not a shelf, as one row of single-choice chips.
 *
 * Chips rather than tabs: with Liked and Offline lifted into [LibraryShelves], what is left is a
 * set of ways to browse, not a set of places. While a shelf is open no chip is selected.
 * Scrollable so each label stays whole however many sections are shown. The chosen chip morphs
 * from tight corners into a pill and gains a check.
 */
@Composable
internal fun LibrarySectionChips(
    tabs: List<LibraryTab>,
    current: LibraryTab?,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = tabs.filterNot { it in ShelfTabs }
    if (sections.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = scheme.surfaceContainerHigh,
        labelColor = scheme.onSurface,
        selectedContainerColor = scheme.primaryContainer,
        selectedLabelColor = scheme.onPrimaryContainer,
        selectedLeadingIconColor = scheme.onPrimaryContainer
    )
    val shapes = FilterChipDefaults.shapes(shape = ChipRestingShape, selectedShape = CircleShape)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        sections.forEach { entry ->
            val selected = entry == current
            FilterChip(
                selected = selected,
                onClick = { onSelect(entry) },
                label = { Text(stringResource(entry.label), maxLines = 1, softWrap = false) },
                shapes = shapes,
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                colors = colors,
                border = null,
                modifier = Modifier.height(40.dp)
            )
        }
    }
}
