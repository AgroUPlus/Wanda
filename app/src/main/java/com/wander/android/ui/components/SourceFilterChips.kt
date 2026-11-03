package com.wander.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.nameRes

/**
 * "All" plus one button per connected backend, as a connected toggle group that scrolls sideways
 * when the backends do not all fit. Choosing the chosen backend again goes back to "All".
 */
@Composable
fun SourceFilterChips(
    sources: List<SourceType>,
    selected: SourceType?,
    onSelect: (SourceType?) -> Unit,
    modifier: Modifier = Modifier,
    /** When false the row still occupies its space but does not respond — see `LibraryScreen`. */
    enabled: Boolean = true,
    /** Home passes `SourceType::shortNameRes` — the row is narrower there and can't spare the width. */
    label: (SourceType) -> Int = SourceType::nameRes
) {
    val options = remember(sources) { listOf<SourceType?>(null) + sources }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ConnectedToggleGroup(
            options = options,
            selected = selected,
            label = { stringResource(it?.let(label) ?: R.string.common_all) },
            onSelect = { onSelect(if (it == selected) null else it) },
            equalWidth = false,
            enabled = enabled
        )
    }
}
