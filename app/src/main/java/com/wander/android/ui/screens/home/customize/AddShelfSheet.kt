package com.wander.android.ui.screens.home.customize

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.wander.android.R
import com.wander.android.ui.components.GroupedInnerRadius
import com.wander.android.ui.components.GroupedItemGap
import com.wander.android.ui.components.GroupedOuterRadius
import com.wander.android.ui.screens.home.ExtraShelf
import com.wander.android.ui.screens.home.HomeSection

private val PressedRadius = 28.dp

/** One row of the sheet. */
private class AddOption(
    val key: String,
    val title: String,
    val summary: String?,
    val icon: ImageVector,
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
        AddOption("genre", stringResource(R.string.shelf_genre), stringResource(R.string.shelf_genre_summary), Icons.Rounded.Category, onCreateGenre)
    )
    val more = extras.map {
        AddOption(it.id, stringResource(it.title), stringResource(it.summary), it.icon()) { onAddExtra(it.id) }
    }
    val restore = removed.map { AddOption(it.id, it.title, null, Icons.Rounded.Restore) { onRestore(it.id) } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
}

private fun LazyListScope.group(label: Int, prefix: String, options: List<AddOption>) {
    if (options.isEmpty()) return
    item(key = "$prefix-label") {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp).animateItem()
        )
    }
    itemsIndexed(options, key = { _, option -> "$prefix-${option.key}" }) { index, option ->
        AddShelfRow(option, index, options.size, Modifier.animateItem())
    }
}

@Composable
private fun AddShelfRow(option: AddOption, index: Int, count: Int, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val round by animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "rowRound")
    val top = lerp(if (index == 0) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)
    val bottom = lerp(if (index == count - 1) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)

    Surface(
        onClick = option.onPick,
        interactionSource = interactions,
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
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
