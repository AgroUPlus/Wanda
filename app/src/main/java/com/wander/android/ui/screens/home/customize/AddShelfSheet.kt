package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.WandaSheet
import com.wander.android.ui.components.pressShrink
import com.wander.android.ui.components.rememberPressMorph
import com.wander.android.ui.components.trackPress
import com.wander.android.ui.screens.home.ExtraShelf
import com.wander.android.ui.screens.home.HomeSection
import com.wander.android.ui.screens.home.ShelfOrigin
import com.wander.android.ui.screens.home.shelfOrigin

/** One row of the sheet. */
private class AddOption(
    val key: String,
    val title: String,
    val summary: String?,
    val icon: ImageVector,
    val origin: ShelfOrigin,
    val onPick: () -> Unit
)

/**
 * What can be put on Home: a genre shelf of your own, the optional shelves, and default shelves that
 * were taken off. Rows are grouped cards that round off as they are pressed, and drop out of the
 * list as they are added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddShelfSheet(
    removed: List<HomeSection>,
    extras: List<ExtraShelf>,
    onRestore: (String) -> Unit,
    onAddExtra: (String) -> Unit,
    onCreateGenre: () -> Unit,
    onDismiss: () -> Unit
) {
    val create = listOf(
        AddOption("genre", stringResource(R.string.shelf_genre), stringResource(R.string.shelf_genre_summary), Icons.Rounded.Category, ShelfOrigin.LIBRARY, onCreateGenre)
    )
    val more = extras.map {
        AddOption(it.id, stringResource(it.title), stringResource(it.summary), it.icon(), shelfOrigin(it.id)) { onAddExtra(it.id) }
    }
    val restorable = removed.map { AddOption(it.id, it.title, null, Icons.Rounded.Restore, shelfOrigin(it.id)) { onRestore(it.id) } }
    val restore = restorable.filter { it.origin == ShelfOrigin.LIBRARY }
    val youtube = restorable.filter { it.origin == ShelfOrigin.YOUTUBE_MUSIC }
    val agro = restorable.filter { it.origin == ShelfOrigin.AGRO }

    WandaSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(GroupedItemGap)
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.home_shelf_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            group(R.string.home_shelf_add_create, "create", create)
            group(R.string.home_shelf_add_more, "more", more)
            // These shelves come from a service rather than the library, so when none is on offer the
            // sheet says why instead of leaving the group out and the question unanswered.
            group(R.string.home_shelf_add_youtube, "youtube", youtube, R.string.home_shelf_add_youtube_hint)
            group(R.string.home_shelf_add_agro, "agro", agro, R.string.home_shelf_add_agro_hint)
            group(R.string.home_shelf_add_removed, "removed", restore)
        }
    }
}

private fun ExtraShelf.icon(): ImageVector = when (this) {
    ExtraShelf.REDISCOVER -> Icons.Rounded.History
    ExtraShelf.HEAVY_ROTATION -> Icons.Rounded.LocalFireDepartment
    ExtraShelf.FRESH -> Icons.Rounded.NewReleases
    ExtraShelf.LATE_NIGHT -> Icons.Rounded.Bedtime
    ExtraShelf.RANDOM -> Icons.Rounded.Shuffle
    ExtraShelf.FRIENDS -> Icons.Rounded.People
}

private fun LazyListScope.group(label: Int, prefix: String, options: List<AddOption>, emptyHint: Int? = null) {
    if (options.isEmpty() && emptyHint == null) return
    item(key = "$prefix-label") {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp).animateItem()
        )
    }
    if (options.isEmpty() && emptyHint != null) {
        item(key = "$prefix-hint") {
            Text(
                text = stringResource(emptyHint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).animateItem()
            )
        }
    }
    itemsIndexed(options, key = { _, option -> "$prefix-${option.key}" }) { index, option ->
        AddShelfRow(option, index, options.size, Modifier.animateItem())
    }
}

@Composable
private fun AddShelfRow(option: AddOption, index: Int, count: Int, modifier: Modifier = Modifier) {
    val morph = rememberPressMorph()

    Surface(
        onClick = option.onPick,
        shape = morph.shape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().trackPress(morph)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.pressShrink(morph).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(shape = CircleShape, color = option.origin.accent(), modifier = Modifier.size(40.dp)) {
                Icon(option.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(option.title, style = MaterialTheme.typography.bodyLarge)
                option.summary?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
