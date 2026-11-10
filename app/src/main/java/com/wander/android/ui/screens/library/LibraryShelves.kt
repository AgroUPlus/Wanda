package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wander.android.R

/** The sections pinned above the others as tiles instead of sitting in the chip row. */
internal val ShelfTabs = setOf(LibraryTab.LIKED, LibraryTab.DOWNLOADS)

/**
 * Liked and Offline as two tiles side by side, each with its track count.
 *
 * They are the two collections you made yourself, so they get the larger target and a count you
 * can read without opening them. Tapping one opens it in the pager like any other section; a
 * section hidden in the layout settings has no tile.
 */
@Composable
internal fun LibraryShelves(
    tabs: List<LibraryTab>,
    current: LibraryTab?,
    likedCount: Int,
    downloadedCount: Int,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val shelves = tabs.filter { it in ShelfTabs }
    if (shelves.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        shelves.forEachIndexed { index, tab ->
            ShelfTile(
                tab = tab,
                count = if (tab == LibraryTab.LIKED) likedCount else downloadedCount,
                selected = tab == current,
                shape = rowShape(index, shelves.size),
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShelfTile(
    tab: LibraryTab,
    count: Int,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isLiked = tab == LibraryTab.LIKED
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier
            .height(72.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Surface(
                shape = (if (isLiked) MaterialShapes.Cookie9Sided else MaterialShapes.Clover4Leaf).toShape(),
                color = if (selected) colors.primary else colors.secondaryContainer,
                contentColor = if (selected) colors.onPrimary else colors.onSecondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(tab.label),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pluralStringResource(R.plurals.library_track_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** Round on the row's outer edges, tight at the seam, like the app's grouped lists turned sideways. */
private fun rowShape(index: Int, count: Int): RoundedCornerShape {
    val start = if (index == 0) Outer else Inner
    val end = if (index == count - 1) Outer else Inner
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

private val Outer = 26.dp
private val Inner = 8.dp
