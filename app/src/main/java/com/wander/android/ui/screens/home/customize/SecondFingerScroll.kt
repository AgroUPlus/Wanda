package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput

/**
 * While one finger holds a shelf, a second finger sliding up or down scrolls the page, so a shelf
 * can be carried a long way without waiting for the edge to scroll it. The first finger of a
 * gesture is left alone; with a single finger down this does nothing, and consumes nothing.
 */
internal fun Modifier.secondFingerScroll(listState: LazyListState): Modifier = pointerInput(listState) {
    awaitPointerEventScope {
        var first: PointerId? = null
        while (true) {
            val changes = awaitPointerEvent(PointerEventPass.Initial).changes
            if (changes.none { it.pressed }) {
                first = null
                continue
            }
            if (changes.none { it.id == first && it.pressed }) first = changes.first { it.pressed }.id
            changes
                .filter { it.id != first && it.pressed && it.previousPressed }
                .forEach { listState.dispatchRawDelta(-(it.position.y - it.previousPosition.y)) }
        }
    }
}
