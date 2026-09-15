package com.wander.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wander.android.data.model.SearchKind

/**
 * Music / Videos / Podcasts.
 *
 * The three are mutually exclusive and cover the whole space of what a search can be, so they sit
 * in one connected row with a single highlight gliding between them on selection, rather than each
 * one snapping its own background on and off.
 */
@Composable
fun SearchKindToggle(
    selected: SearchKind,
    onSelect: (SearchKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightState = rememberTravelingHighlightState()

    Box(modifier = modifier.fillMaxWidth()) {
        TravelingHighlight(state = highlightState, selectedKey = selected)

        Row(modifier = Modifier.fillMaxWidth()) {
            SearchKind.entries.forEach { kind ->
                SelectableChip(
                    label = kind.label,
                    selected = kind == selected,
                    onClick = { onSelect(kind) },
                    highlightState = highlightState,
                    key = kind,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
