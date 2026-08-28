package com.wander.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.SourceType

/** Which backend Home is showing. */
@Composable
internal fun SourceChipRow(
    sources: List<SourceType>,
    selected: SourceType?,
    onSelect: (SourceType?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        modifier = modifier
    ) {
        item(key = "all", contentType = "source-chip") {
            SourceToggle(
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
            SourceToggle(
                label = source.displayName,
                selected = selected == source,
                // Tapping the active chip clears it, so the row needs no separate escape.
                onClick = { onSelect(source.takeIf { selected != source }) }
            )
        }
    }
}

/**
 * One source, as an expressive toggle rather than a `FilterChip`.
 *
 * The difference is the shape: a toggle button is a pill at rest and squares off as it is
 * selected, so which source is active is legible from the silhouette before the checkmark or the
 * fill colour is read at all. A row of identically-shaped chips distinguished only by tint is the
 * stock Material 2 answer, and next to the shaped play buttons on the album and artist pages it
 * looked like it had been left behind.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() }
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(ToggleButtonDefaults.IconSize)
            )
        }
        Text(text = label, modifier = Modifier.padding(start = if (selected) 8.dp else 0.dp))
    }
}
