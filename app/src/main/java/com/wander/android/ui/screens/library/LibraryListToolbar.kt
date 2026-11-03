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
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.shortNameRes
import com.wander.android.ui.components.SourceFilterChips

/**
 * The row above a Library list: the source filter where there is more than one source to pick
 * from, and the sort button at the end for a list that can be sorted.
 *
 * The filter is Home's row — "All" or one source, with the same short names — and one choice for
 * every tab: picking "Subsonic" on Albums and finding Liked still showing YouTube would read as the
 * filter failing.
 */
@Composable
internal fun LibraryListToolbar(tab: LibraryTab, browse: LibraryBrowseViewModel) {
    val sorts by browse.sorts.collectAsStateWithLifecycle()
    val selectedSource by browse.selectedSource.collectAsStateWithLifecycle()
    val sort = sorts[tab]
    val showsFilter = browse.availableSources.size > 1
    if (!showsFilter && sort == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (showsFilter) {
                SourceFilterChips(
                    sources = browse.availableSources,
                    selected = selectedSource,
                    onSelect = browse::selectSource,
                    label = SourceType::shortNameRes
                )
            }
        }
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
