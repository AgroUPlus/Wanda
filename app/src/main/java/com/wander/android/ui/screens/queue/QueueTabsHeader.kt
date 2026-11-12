package com.wander.android.ui.screens.queue

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * The tabs under the drawer's cover: the one showing large and bold, the other small and dim, so
 * the header is also the title of the list under it. [meta] fills the right-hand side — how long
 * is left, or how much has played.
 */
@Composable
internal fun QueueTabsHeader(
    selected: QueueTab,
    onSelect: (QueueTab) -> Unit,
    meta: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 24.dp, top = 4.dp, bottom = 6.dp)
            .selectableGroup()
    ) {
        QueueTab.entries.forEachIndexed { index, tab ->
            if (index > 0) Spacer(Modifier.width(10.dp))
            QueueTabLabel(
                label = tab.label,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.alignByBaseline()
            )
        }
        Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.weight(1f).alignByBaseline()) {
            meta()
        }
    }
}

/** One tab's name, growing into a title as it is picked and shrinking back as it is left. */
@Composable
private fun QueueTabLabel(@StringRes label: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val large = MaterialTheme.typography.headlineSmallEmphasized
    val small = MaterialTheme.typography.titleMediumEmphasized
    val grown by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "queueTabSize"
    )
    val color by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UNSELECTED_ALPHA)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "queueTabColor"
    )
    Text(
        text = stringResource(label),
        style = large.copy(fontSize = lerp(small.fontSize, large.fontSize, grown.coerceAtLeast(0f))),
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

/** Dim enough to read as "the other one", still clearly a word you can tap. */
private const val UNSELECTED_ALPHA = 0.7f
