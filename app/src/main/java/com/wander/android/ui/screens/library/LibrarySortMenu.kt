package com.wander.android.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wander.android.R
import com.wander.android.data.model.LibrarySort

private val TrackSorts = listOf(
    LibrarySort.TITLE,
    LibrarySort.ARTIST,
    LibrarySort.ALBUM,
    LibrarySort.DATE_ADDED,
    LibrarySort.MOST_PLAYED,
    LibrarySort.RECENTLY_PLAYED
)

private val AlbumSorts = listOf(
    LibrarySort.TITLE,
    LibrarySort.ARTIST,
    LibrarySort.YEAR,
    LibrarySort.DATE_ADDED,
    LibrarySort.MOST_PLAYED,
    LibrarySort.RECENTLY_PLAYED
)

/** The orders [this] tab's list offers; empty for a section that is not a sortable list. */
internal val LibraryTab.sortOptions: List<LibrarySort>
    get() = when (this) {
        LibraryTab.TRACKS, LibraryTab.LIKED, LibraryTab.DOWNLOADS -> TrackSorts
        LibraryTab.ALBUMS -> AlbumSorts
        else -> emptyList()
    }

/**
 * Where a list starts before anyone picks an order. Liked keeps the most-recently-played order it
 * always had; everything else reads alphabetically.
 */
internal val LibraryTab.defaultSort: LibrarySort
    get() = if (this == LibraryTab.LIKED) LibrarySort.RECENTLY_PLAYED else LibrarySort.TITLE

@get:StringRes
internal val LibrarySort.label: Int
    get() = when (this) {
        LibrarySort.TITLE -> R.string.library_sort_title
        LibrarySort.ARTIST -> R.string.library_sort_artist
        LibrarySort.ALBUM -> R.string.library_sort_album
        LibrarySort.DATE_ADDED -> R.string.library_sort_date_added
        LibrarySort.YEAR -> R.string.library_sort_year
        LibrarySort.MOST_PLAYED -> R.string.library_sort_most_played
        LibrarySort.RECENTLY_PLAYED -> R.string.library_sort_recently_played
    }

/** A sort button that opens the orders [options] offers, with the current one checked. */
@Composable
internal fun LibrarySortMenu(
    options: List<LibrarySort>,
    selected: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Rounded.Sort,
                contentDescription = stringResource(R.string.library_sort_by, stringResource(selected.label))
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
