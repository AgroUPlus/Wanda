package com.wander.android.ui.screens.library

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.EpisodeState
import com.wander.android.ui.components.ConnectedToggleGroup

private val EpisodeFilters: List<EpisodeState?> = listOf(null) + EpisodeState.entries

/** "All" plus one button per listening state, as a connected toggle group. */
@Composable
internal fun EpisodeFilterChips(
    selected: EpisodeState?,
    onSelect: (EpisodeState?) -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectedToggleGroup(
        options = EpisodeFilters,
        selected = selected,
        label = { stringResource(it?.label ?: R.string.common_all) },
        onSelect = onSelect,
        modifier = modifier.padding(horizontal = 20.dp)
    )
}

private val EpisodeState.label: Int
    get() = when (this) {
        EpisodeState.IN_PROGRESS -> R.string.podcasts_filter_in_progress
        EpisodeState.UNPLAYED -> R.string.podcasts_filter_unplayed
        EpisodeState.FINISHED -> R.string.podcasts_filter_finished
    }
