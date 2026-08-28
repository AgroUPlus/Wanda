package com.wander.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wander.android.data.model.SearchKind

/**
 * Music / Videos / Podcasts.
 *
 * A connected group rather than a row of filter chips. The three are mutually exclusive and cover
 * the whole space of what a search can be, which is exactly what a joined control means — where
 * chips read as independent filters you might toggle in any combination, and sat confusingly next
 * to the source chips below, which genuinely are that.
 *
 * `ButtonGroup` rather than the segmented row it replaces: same meaning, but the members squash and
 * their neighbours give way as one is pressed, which is the expressive idiom the rest of the app
 * now uses. The old `SegmentedButton` had no motion at all.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchKindToggle(
    selected: SearchKind,
    onSelect: (SearchKind) -> Unit,
    modifier: Modifier = Modifier
) {
    ButtonGroup(
        // Nothing overflows: there are exactly three kinds and they fit any phone.
        overflowIndicator = {},
        modifier = modifier.fillMaxWidth()
    ) {
        SearchKind.entries.forEach { kind ->
            // The group renders the label itself, so there is no content slot to fill here.
            toggleableItem(
                checked = kind == selected,
                label = kind.label,
                onCheckedChange = { onSelect(kind) },
                weight = 1f
            )
        }
    }
}
