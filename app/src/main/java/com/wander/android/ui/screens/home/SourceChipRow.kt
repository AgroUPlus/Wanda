package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.SourceType

/**
 * Narrows Home to one backend.
 *
 * Filters what is already on screen rather than refetching, so it is a way of *looking* at the
 * library, not a mode you wait for. Absent below two sources, where it would only ever have one
 * meaningful state.
 */
@Composable
internal fun SourceChipRow(
    sources: List<SourceType>,
    selected: SourceType?,
    onSelect: (SourceType?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
    ) {
        item(key = "all", contentType = "source-chip") {
            SourceChip(
                label = "All",
                selected = selected == null,
                onClick = { onSelect(null) }
            )
        }
        items(
            items = sources,
            key = { it.name },
            contentType = { "source-chip" }
        ) { source ->
            SourceChip(
                label = source.displayName,
                selected = selected == source,
                // Tapping the active chip clears it, so the row needs no separate escape.
                onClick = { onSelect(source.takeIf { selected != source }) }
            )
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null) }
        } else {
            null
        }
    )
}
