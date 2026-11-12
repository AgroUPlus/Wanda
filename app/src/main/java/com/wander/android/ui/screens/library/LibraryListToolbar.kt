package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.shortNameRes
import com.wander.android.ui.components.SourceFilterChips

/**
 * The rows above a Library list: the source filter where there is more than one source to pick
 * from, then the list's own controls — its order, the Downloaded filter for a track list, and the
 * list/grid switch for a tab that has both.
 *
 * The source filter is Home's row — "All" or one source, with the same short names — and one choice
 * for every tab: picking "Subsonic" on Albums and finding Liked still showing YouTube would read as
 * the filter failing. It keeps a row of its own: beside the sort and the switch it would scroll
 * away under them.
 */
@Composable
internal fun LibraryListToolbar(tab: LibraryTab, browse: LibraryBrowseViewModel) {
    val sorts by browse.sorts.collectAsStateWithLifecycle()
    val views by browse.views.collectAsStateWithLifecycle()
    val selectedSource by browse.selectedSource.collectAsStateWithLifecycle()
    val downloadedOnly by browse.downloadedOnly.collectAsStateWithLifecycle()
    val sort = sorts[tab]
    val view = views[tab]
    val showsFilter = browse.availableSources.size > 1
    val showsControls = sort != null || view != null || tab.offersDownloadedFilter
    if (!showsFilter && !showsControls) return
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showsFilter) {
            SourceFilterChips(
                sources = browse.availableSources,
                selected = selectedSource,
                onSelect = browse::selectSource,
                label = SourceType::shortNameRes,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (!showsControls) return@Column
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 8.dp, end = 12.dp)
        ) {
            if (sort != null) {
                LibrarySortMenu(
                    options = tab.sortOptions,
                    selected = sort,
                    onSelect = { browse.setSort(tab, it) }
                )
            }
            Spacer(Modifier.weight(1f))
            if (tab.offersDownloadedFilter) {
                DownloadedFilterChip(downloadedOnly, browse::setDownloadedOnly)
            }
            if (view != null) {
                LibraryViewToggle(
                    selected = view,
                    onSelect = { browse.setView(tab, it) },
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}

/** Narrows a track list to the tracks on this device; outlined until it is on. */
@Composable
private fun DownloadedFilterChip(selected: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(stringResource(R.string.library_filter_downloaded), maxLines = 1) },
        leadingIcon = {
            Icon(Icons.Rounded.DownloadDone, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
        },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = scheme.tertiaryContainer,
            selectedLabelColor = scheme.onTertiaryContainer,
            selectedLeadingIconColor = scheme.onTertiaryContainer
        )
    )
}
