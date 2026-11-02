package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The row above a sortable Library list: the source filter where there is more than one source to
 * pick from, and the sort button at the end.
 */
@Composable
internal fun LibraryListToolbar(
    tab: LibraryTab,
    browse: LibraryBrowseViewModel,
    filter: @Composable () -> Unit = {}
) {
    val sorts by browse.sorts.collectAsStateWithLifecycle()
    val sort = sorts[tab]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) { filter() }
        if (sort != null) {
            LibrarySortMenu(
                options = tab.sortOptions,
                selected = sort,
                onSelect = { browse.setSort(tab, it) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
