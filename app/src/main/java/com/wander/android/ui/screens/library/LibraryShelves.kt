package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** The sections pinned above the others as tiles instead of sitting in the chip row. */
internal val ShelfTabs = setOf(LibraryTab.LIKED, LibraryTab.DOWNLOADS)

/**
 * Liked, Offline, Recently added and Most played as one connected two-by-two group.
 *
 * Liked and Offline are the collections you made yourself: they open in the pager like any other
 * section and show their track count. The two shortcuts play that slice of the library at once.
 * A section hidden in the layout settings, or a shortcut with nothing behind it, has no tile.
 */
@Composable
internal fun LibraryShelves(
    tabs: List<LibraryTab>,
    current: LibraryTab?,
    likedCount: Int,
    downloadedCount: Int,
    shortcuts: List<LibraryShortcut>,
    onSelect: (LibraryTab) -> Unit,
    onPlayShortcut: (LibraryShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = tabs.filter { it in ShelfTabs }.map { tab ->
        Shelf.Section(tab, if (tab == LibraryTab.LIKED) likedCount else downloadedCount)
    } + shortcuts.map(Shelf::Shortcut)
    if (cells.isEmpty()) return

    val rows = cells.chunked(COLUMNS)
    Column(
        verticalArrangement = Arrangement.spacedBy(GAP),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        rows.forEachIndexed { row, cellsInRow ->
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                cellsInRow.forEachIndexed { column, cell ->
                    val shape = gridShape(row, column, cells.size)
                    val tile = Modifier.weight(1f)
                    when (cell) {
                        is Shelf.Section -> SectionTile(cell, cell.tab == current, shape, onSelect, tile)
                        is Shelf.Shortcut -> ShortcutTile(cell.shortcut, shape, onPlayShortcut, tile)
                    }
                }
                // A lone last tile keeps its half width rather than stretching across the row.
                if (cellsInRow.size < COLUMNS) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private sealed interface Shelf {
    data class Section(val tab: LibraryTab, val count: Int) : Shelf
    data class Shortcut(val shortcut: LibraryShortcut) : Shelf
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionTile(
    cell: Shelf.Section,
    selected: Boolean,
    shape: RoundedCornerShape,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier
) {
    val isLiked = cell.tab == LibraryTab.LIKED
    LibraryShelfTile(
        title = stringResource(cell.tab.label),
        subtitle = pluralStringResource(R.plurals.library_track_count, cell.count, cell.count),
        icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.DownloadDone,
        badge = if (isLiked) MaterialShapes.Cookie9Sided else MaterialShapes.Clover4Leaf,
        selected = selected,
        shape = shape,
        onClick = { onSelect(cell.tab) },
        modifier = modifier.semantics {
            this.selected = selected
            role = Role.Tab
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortcutTile(
    shortcut: LibraryShortcut,
    shape: RoundedCornerShape,
    onPlay: (LibraryShortcut) -> Unit,
    modifier: Modifier
) {
    val isRecent = shortcut == LibraryShortcut.RECENTLY_ADDED
    val title = stringResource(
        if (isRecent) R.string.library_shortcut_recently_added else R.string.library_shortcut_most_played
    )
    val description = stringResource(R.string.library_shortcut_play, title)
    LibraryShelfTile(
        title = title,
        subtitle = stringResource(
            if (isRecent) R.string.library_shortcut_recently_added_hint else R.string.library_shortcut_most_played_hint
        ),
        icon = if (isRecent) Icons.Rounded.LibraryAdd else Icons.AutoMirrored.Rounded.TrendingUp,
        badge = if (isRecent) MaterialShapes.Pill else MaterialShapes.Sunny,
        selected = false,
        shape = shape,
        onClick = { onPlay(shortcut) },
        modifier = modifier.semantics { contentDescription = description }
    )
}

/**
 * Round where the group meets the outside, tight where a tile meets a neighbour: a corner is
 * outer only when there is no tile beside it on either of its two sides.
 */
private fun gridShape(row: Int, column: Int, count: Int): RoundedCornerShape {
    fun exists(r: Int, c: Int) = r >= 0 && c in 0 until COLUMNS && r * COLUMNS + c < count
    fun corner(dr: Int, dc: Int) = if (exists(row + dr, column) || exists(row, column + dc)) Inner else Outer
    return RoundedCornerShape(
        topStart = corner(-1, -1),
        topEnd = corner(-1, 1),
        bottomEnd = corner(1, 1),
        bottomStart = corner(1, -1)
    )
}

private const val COLUMNS = 2
private val GAP = 4.dp
private val Outer = 26.dp
private val Inner = 8.dp
