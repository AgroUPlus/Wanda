package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.SourceType

/** "All" plus one chip per connected backend. Sources that are not set up are not listed. */
@Composable
fun SourceFilterChips(
    sources: List<SourceType>,
    selected: SourceType?,
    onSelect: (SourceType?) -> Unit,
    modifier: Modifier = Modifier,
    /** When false the row still occupies its space but does not respond — see `LibraryScreen`. */
    enabled: Boolean = true
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        userScrollEnabled = enabled,
        modifier = modifier
    ) {
        item(key = "all") {
            ToggleButton(
                checked = selected == null,
                enabled = enabled,
                onCheckedChange = { onSelect(null) }
            ) { Text("All") }
        }
        items(sources, key = { it.name }) { source ->
            ToggleButton(
                checked = selected == source,
                enabled = enabled,
                onCheckedChange = { onSelect(if (selected == source) null else source) }
            ) { Text(source.displayName) }
        }
    }
}
