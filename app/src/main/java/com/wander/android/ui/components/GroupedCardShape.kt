package com.wander.android.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The per-row corner rounding [GroupedCard] uses — tight on the edge a row shares with a
 * neighbour, full on the edge it doesn't — shared out so a lazy/reorderable list that can't use
 * [GroupedCard] itself (it isn't lazy, and has no concept of a drag) can still match its grouped
 * look row by row.
 */
fun groupedItemShape(index: Int, count: Int): RoundedCornerShape {
    val isFirst = index == 0
    val isLast = index == count - 1
    val top = if (isFirst) GroupedOuterRadius else GroupedInnerRadius
    val bottom = if (isLast) GroupedOuterRadius else GroupedInnerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** [com.wander.android.ui.theme.WandaShapes.large] — a group's outer corners. */
val GroupedOuterRadius = 26.dp

/** [com.wander.android.ui.theme.WandaShapes.extraSmall] — the edge a row shares with a neighbour. */
val GroupedInnerRadius = 8.dp

/** The physical gap between rows, not just a divider — small enough to read as one group. */
val GroupedItemGap = 2.dp
