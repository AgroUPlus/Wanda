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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wander.android.R
import com.wander.android.data.model.SourceType
import com.wander.android.data.model.shortNameRes

/**
 * Which backends a search actually queries — several at once, not one at a time.
 *
 * The same row as Home's [SourceFilterChips] — same names, same badges — with several allowed on.
 * "All" is a shortcut, not a state: it selects everything, and clears to the default when
 * everything is already on.
 */
@Composable
fun SourceToggleChips(
    sources: List<SourceType>,
    selected: Set<SourceType>,
    onToggle: (SourceType) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSelected = selected.containsAll(sources)
    val options = remember(sources) { listOf<SourceType?>(null) + sources }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ConnectedToggleButtons(
            options = options,
            isChecked = { if (it == null) allSelected else it in selected },
            role = Role.Checkbox,
            // The same short, translated names as Home's row: `displayName` is the English one for
            // logs, and showing it here was why the two rows read differently.
            label = { stringResource(it?.shortNameRes ?: R.string.common_all) },
            onSelect = { source ->
                when {
                    source == null -> onSelectAll()
                    source !in selected || selected.size > 1 -> onToggle(source)
                }
            },
            equalWidth = false,
            leadingIcon = { source, checked -> SourceChipMark(source, checked) }
        )
    }
}
