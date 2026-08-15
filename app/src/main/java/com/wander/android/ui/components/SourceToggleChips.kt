package com.wander.android.ui.components

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
 * Which backends a search actually queries — several at once, not one at a time.
 *
 * This is a *toggle* row rather than the single-choice one in the Library, because here the
 * selection decides what gets asked, not just what gets shown. Turning a slow backend off has to
 * make the search faster, which filtering results afterwards never could.
 *
 * "All" is a shortcut, not a state: it selects everything, and clears to the default when
 * everything is already on.
 */
@Composable
fun SourceToggleChips(
    sources: List<SourceType>,
    selected: Set<SourceType>,
    onToggle: (SourceType) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSelected = selected.containsAll(sources)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
    ) {
        item(key = "all") {
            FilterChip(
                selected = allSelected,
                onClick = onSelectAll,
                label = { Text("All") },
                leadingIcon = if (allSelected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                } else {
                    null
                }
            )
        }
        items(sources, key = { it.name }) { source ->
            val isOn = source in selected
            FilterChip(
                selected = isOn,
                // Turning the last one off would search nothing, so the row always keeps one on.
                enabled = !isOn || selected.size > 1,
                onClick = { onToggle(source) },
                label = { Text(source.displayName) },
                leadingIcon = if (isOn) {
                    { Icon(Icons.Rounded.Check, contentDescription = null) }
                } else {
                    null
                }
            )
        }
    }
}
