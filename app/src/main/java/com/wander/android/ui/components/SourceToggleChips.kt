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

/**
 * Which backends a search actually queries — several at once, not one at a time.
 *
 * Expressive connected ButtonGroup where members squash and expand dynamically.
 *
 * "All" is a shortcut, not a state: it selects everything, and clears to the default when
 * everything is already on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SourceToggleChips(
    sources: List<SourceType>,
    selected: Set<SourceType>,
    onToggle: (SourceType) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSelected = selected.containsAll(sources)

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        toggleableItem(
            checked = allSelected,
            label = "All",
            onCheckedChange = { onSelectAll() }
        )
        sources.forEach { source ->
            val isOn = source in selected
            toggleableItem(
                checked = isOn,
                label = source.displayName,
                onCheckedChange = {
                    if (!isOn || selected.size > 1) {
                        onToggle(source)
                    }
                }
            )
        }
    }
}
