package com.wander.android.ui.screens.home.customize

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restore
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
import com.wander.android.ui.screens.home.HomeSection

private val PressedRadius = 28.dp

/**
 * What can be put on Home: shelves that were taken off, then genres from the library. Rows are
 * grouped cards that round off as they are pressed, and drop out of the list as they are added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddShelfSheet(
    removed: List<HomeSection>,
    genres: List<String>,
    onRestore: (String) -> Unit,
    onAddGenre: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
            if (removed.isEmpty() && genres.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.home_shelf_add_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
            }
            group(R.string.home_shelf_add_removed, "removed", removed, { it.id }, { it.title }, Icons.Rounded.Restore) {
                onRestore(it.id)
            }
            group(R.string.home_shelf_add_genres, "genre", genres, { it }, { it }, Icons.Rounded.MusicNote, onAddGenre)
        }
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.group(
    label: Int,
    prefix: String,
    items: List<T>,
    id: (T) -> String,
    title: (T) -> String,
    icon: ImageVector,
    onPick: (T) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "$prefix-label") {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
        )
    }
    itemsIndexed(items, key = { _, item -> "$prefix-${id(item)}" }) { index, item ->
        AddShelfRow(
            title = title(item),
            icon = icon,
            index = index,
            count = items.size,
            onClick = { onPick(item) },
            modifier = Modifier.animateItem()
        )
    }
}

@Composable
private fun AddShelfRow(
    title: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val round by animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "rowRound")
    val top = lerp(if (index == 0) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)
    val bottom = lerp(if (index == count - 1) GroupedOuterRadius else GroupedInnerRadius, PressedRadius, round)

    Surface(
        onClick = onClick,
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
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(10.dp))
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
