package com.wander.android.ui.screens.home

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

/**
 * Which backend Home is showing.
 *
 * Connected expressive ButtonGroup where the active item expands and neighbours compress dynamically.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SourceChipRow(
    sources: List<SourceType>,
    selected: SourceType?,
    onSelect: (SourceType?) -> Unit,
    modifier: Modifier = Modifier
) {
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        toggleableItem(
            checked = selected == null,
            label = "All",
            onCheckedChange = { onSelect(null) }
        )
        sources.forEach { source ->
            toggleableItem(
                checked = selected == source,
                label = source.shortName,
                // Tapping the active chip clears it, so the row needs no separate escape.
                onCheckedChange = { onSelect(source.takeIf { selected != source }) }
            )
        }
    }
}
