package com.wander.android.ui.screens.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.EpisodeState
import com.wander.android.ui.components.SelectableChip
import com.wander.android.ui.components.TravelingHighlight
import com.wander.android.ui.components.rememberTravelingHighlightState

private const val AllEpisodesKey = "all"

/** "All" plus one chip per listening state — the same gliding-highlight row as the source filter. */
@Composable
internal fun EpisodeFilterChips(
    selected: EpisodeState?,
    onSelect: (EpisodeState?) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlightState = rememberTravelingHighlightState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        TravelingHighlight(state = highlightState, selectedKey = selected ?: AllEpisodesKey)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip(
                label = stringResource(R.string.common_all),
                selected = selected == null,
                onClick = { onSelect(null) },
                highlightState = highlightState,
                key = AllEpisodesKey
            )
            EpisodeState.entries.forEach { state ->
                SelectableChip(
                    label = stringResource(state.label),
                    selected = selected == state,
                    onClick = { onSelect(state) },
                    highlightState = highlightState,
                    key = state
                )
            }
        }
    }
}

private val EpisodeState.label: Int
    get() = when (this) {
        EpisodeState.IN_PROGRESS -> R.string.podcasts_filter_in_progress
        EpisodeState.UNPLAYED -> R.string.podcasts_filter_unplayed
        EpisodeState.FINISHED -> R.string.podcasts_filter_finished
    }
