package com.wander.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.wander.android.data.model.SearchKind

/**
 * Music / Videos / Podcasts.
 *
 * A segmented button rather than a row of filter chips. The three are mutually exclusive and cover
 * the whole space of what a search can be, which is exactly what this control means — where chips
 * read as independent filters you might toggle in any combination, and sat confusingly next to the
 * source chips below, which genuinely are that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchKindToggle(
    selected: SearchKind,
    onSelect: (SearchKind) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SearchKind.entries.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = SearchKind.entries.size
                ),
                label = {
                    Text(
                        text = kind.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
