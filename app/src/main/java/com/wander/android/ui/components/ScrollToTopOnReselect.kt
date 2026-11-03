package com.wander.android.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import com.wander.android.ui.navigation.ReselectEffect
import com.wander.android.ui.theme.LocalReducedMotion

/**
 * Past this many items an animated scroll to the top takes long enough to feel like waiting, so
 * the list jumps to here first and animates only the last stretch — still visibly travelling up.
 */
internal const val ScrollHomeJumpIndex = 24

/** Where to jump before animating the rest of the way up from [firstVisibleIndex], if at all. */
internal fun scrollHomeJumpTarget(firstVisibleIndex: Int): Int? =
    ScrollHomeJumpIndex.takeIf { firstVisibleIndex > it }

/** Scrolls [state] back to its first item when its tab is tapped again. */
@Composable
internal fun ScrollToTopOnReselect(state: LazyListState) {
    val reducedMotion = LocalReducedMotion.current
    ReselectEffect {
        scrollHome(
            firstVisibleIndex = state.firstVisibleItemIndex,
            reducedMotion = reducedMotion,
            jumpTo = state::scrollToItem,
            animateToTop = { state.animateScrollToItem(0) }
        )
    }
}

/** The grid counterpart of the [LazyListState] overload. */
@Composable
internal fun ScrollToTopOnReselect(state: LazyGridState) {
    val reducedMotion = LocalReducedMotion.current
    ReselectEffect {
        scrollHome(
            firstVisibleIndex = state.firstVisibleItemIndex,
            reducedMotion = reducedMotion,
            jumpTo = state::scrollToItem,
            animateToTop = { state.animateScrollToItem(0) }
        )
    }
}

private suspend fun scrollHome(
    firstVisibleIndex: Int,
    reducedMotion: Boolean,
    jumpTo: suspend (Int) -> Unit,
    animateToTop: suspend () -> Unit
) {
    if (reducedMotion) {
        jumpTo(0)
        return
    }
    scrollHomeJumpTarget(firstVisibleIndex)?.let { jumpTo(it) }
    animateToTop()
}
