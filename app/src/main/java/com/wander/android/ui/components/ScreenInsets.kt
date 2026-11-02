package com.wander.android.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Splits the app Scaffold's content padding for screens with a fixed header above a scrolling
 * list.
 *
 * The whole padding cannot go on the list: its top inset would then sit *below* the header,
 * leaving the header itself under the status bar. Nor can it all go on the outer container, since
 * the list should scroll its content under the bottom bar rather than stop short of it.
 *
 * So the header takes [headerInset] and the list takes [listInset]. Both keep the start and end
 * edges: in landscape the three-button navigation bar and a display cutout sit on a *side* of the
 * window, and the shell folds both into this padding — dropping them put rows under the bar.
 *
 * A list that is itself inside a container already padded with [headerInset] — a `Column` holding
 * the title and the list together — takes [nestedListInset] instead, or it would be inset twice.
 */
@Composable
fun PaddingValues.headerInset(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction)
    )
}

/**
 * The bottom and side insets — lets list content scroll beneath the mini player and nav bar while
 * keeping clear of a side navigation bar or cutout. [extraBottom] is added below the last item.
 */
@Composable
fun PaddingValues.listInset(extraBottom: Dp = 0.dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + extraBottom
    )
}

/** The bottom inset only, for a list whose container already took [headerInset]'s side edges. */
@Composable
fun PaddingValues.nestedListInset(): PaddingValues =
    PaddingValues(bottom = calculateBottomPadding())
