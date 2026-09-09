package com.wander.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.SourceType

/** "All" plus one chip per connected backend with expressive morph animation. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SourceFilterChips(
    sources: List<SourceType>,
    selected: SourceType?,
    onSelect: (SourceType?) -> Unit,
    modifier: Modifier = Modifier,
    /** When false the row still occupies its space but does not respond — see `LibraryScreen`. */
    enabled: Boolean = true
) {
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        toggleableItem(
            checked = selected == null,
            label = "All",
            onCheckedChange = { if (enabled) onSelect(null) }
        )
        sources.forEach { source ->
            toggleableItem(
                checked = selected == source,
                label = source.displayName,
                onCheckedChange = { if (enabled) onSelect(if (selected == source) null else source) }
            )
        }
    }
}
