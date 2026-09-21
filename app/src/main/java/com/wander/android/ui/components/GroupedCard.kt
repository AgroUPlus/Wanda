package com.wander.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A tonal container for a set of related rows — settings sections, and anywhere else a screen
 * offers many options that read better grouped than loose in the page background.
 *
 * Matches the real Android Settings app's own grouped-preference redesign (Android 15 QPR1): a
 * small physical gap between rows rather than one unbroken surface, each row rounded on its own —
 * tight on the edge it shares with a neighbour, full on the edge it doesn't. [OuterRadius]/
 * [InnerRadius] mirror this app's own shape scale ([com.wander.android.ui.theme.WandaShapes]
 * `large`/`extraSmall`) rather than inventing new numbers.
 *
 * Takes a list, not a free-form `@Composable` block: knowing which row is first/last is what makes
 * the per-row shape possible, and a list is the one representation that can't get that count wrong
 * the way a caller manually tracking "am I the last one" across a handful of `if`s could.
 */
@Composable
fun GroupedCard(modifier: Modifier = Modifier, items: List<@Composable () -> Unit>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ItemGap),
        modifier = modifier.fillMaxWidth()
    ) {
        items.forEachIndexed { index, item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = itemShape(index, items.size),
                modifier = Modifier.fillMaxWidth()
            ) {
                item()
            }
        }
    }
}

private fun itemShape(index: Int, count: Int): RoundedCornerShape {
    val isFirst = index == 0
    val isLast = index == count - 1
    val top = if (isFirst) OuterRadius else InnerRadius
    val bottom = if (isLast) OuterRadius else InnerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** [com.wander.android.ui.theme.WandaShapes.large] — the group's outer corners. */
private val OuterRadius = 26.dp

/** [com.wander.android.ui.theme.WandaShapes.extraSmall] — the edge a row shares with a neighbour. */
private val InnerRadius = 8.dp

/** The physical gap between rows, not just a divider — small enough to read as one group. */
private val ItemGap = 2.dp
