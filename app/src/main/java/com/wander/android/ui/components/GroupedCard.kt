package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A tonal container for a set of related rows — settings sections, and anywhere else a screen
 * offers many options that read better grouped than loose in the page background.
 *
 * Matches the real Android Settings app's own grouped-preference redesign (Android 15 QPR1): a
 * small physical gap between rows rather than one unbroken surface, each row rounded on its own —
 * see [groupedItemShape] for the per-row rounding, shared with any lazy/reorderable list ([queue
 * screens][com.wander.android.ui.screens.queue.QueueUpNext] included) that wants the same look but
 * can't use this composable directly.
 *
 * Takes a list, not a free-form `@Composable` block: knowing which row is first/last is what makes
 * the per-row shape possible, and a list is the one representation that can't get that count wrong
 * the way a caller manually tracking "am I the last one" across a handful of `if`s could.
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    items: List<@Composable () -> Unit>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp)
    ) {
        items.forEachIndexed { index, item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = groupedItemShape(index, items.size),
                modifier = Modifier.fillMaxWidth()
            ) {
                item()
            }
        }
    }
}
