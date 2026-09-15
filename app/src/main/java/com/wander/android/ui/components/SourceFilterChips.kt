package com.wander.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.data.model.SourceType

/** Stable key for "All" — a fresh object every recomposition would break bounds tracking. */
private const val AllSourcesKey = "__all_sources__"

/** "All" plus one chip per connected backend, with a highlight that glides between them. */
@Composable
fun SourceFilterChips(
    sources: List<SourceType>,
    selected: SourceType?,
    onSelect: (SourceType?) -> Unit,
    modifier: Modifier = Modifier,
    /** When false the row still occupies its space but does not respond — see `LibraryScreen`. */
    enabled: Boolean = true
) {
    val highlightState = rememberTravelingHighlightState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        TravelingHighlight(state = highlightState, selectedKey = selected ?: AllSourcesKey)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip(
                label = "All",
                selected = selected == null,
                onClick = { onSelect(null) },
                highlightState = highlightState,
                key = AllSourcesKey,
                enabled = enabled
            )
            sources.forEach { source ->
                SelectableChip(
                    label = source.displayName,
                    selected = selected == source,
                    onClick = { onSelect(if (selected == source) null else source) },
                    highlightState = highlightState,
                    key = source,
                    enabled = enabled
                )
            }
        }
    }
}

/** One pill in a [TravelingHighlight]-backed row — the visible label, the highlight is drawn behind it. */
@Composable
internal fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    highlightState: TravelingHighlightState,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = rememberHaptics()
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "chipContentColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .recordHighlightBounds(highlightState, key)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(enabled = enabled) {
                haptics.toggled(!selected)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}
