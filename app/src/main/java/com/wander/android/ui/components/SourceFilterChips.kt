package com.wander.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wander.android.R
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
    enabled: Boolean = true,
    /** Home passes `SourceType::shortName` — the row is narrower there and can't spare the width. */
    label: (SourceType) -> String = SourceType::displayName
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
                label = stringResource(R.string.common_all),
                selected = selected == null,
                onClick = { onSelect(null) },
                highlightState = highlightState,
                key = AllSourcesKey,
                enabled = enabled
            )
            sources.forEach { source ->
                SelectableChip(
                    label = label(source),
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

    // Scale, not ripple, for press feedback — the same M3 Expressive answer `TrackRow`'s artwork
    // gives a press, applied here so every enum-chip row in the app (this one, `SearchKindToggle`,
    // and Home's source filter once it moved onto this component) bounces identically.
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource, label = "chipPress")

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(pressScale)
            .recordHighlightBounds(highlightState, key)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled) {
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
