package com.wander.android.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * How far a scrollable hero header (index 0 of [listState]) has been scrolled past, as 0f (fully
 * visible) to 1f (fully scrolled out).
 *
 * Pure derivation, no animation: a screen with a hero at the top of its [LazyColumn] wants a
 * compact bar to fade in once that hero leaves view, and this is the raw signal for it — callers
 * animate the fraction themselves through `MaterialTheme.motionScheme` (see [CompactHeroTopBar]),
 * this just tracks scroll position.
 *
 * [heroHeightPx] must be measured live via `Modifier.onGloballyPositioned` on the hero item, not
 * assumed: hero height varies with aspect ratio and screen width.
 */
@Composable
fun rememberCollapsingHeaderFraction(
    listState: LazyListState,
    heroHeightPx: Float
): State<Float> = remember(listState) {
    derivedStateOf {
        if (heroHeightPx <= 0f) {
            0f
        } else if (listState.firstVisibleItemIndex > 0) {
            1f
        } else {
            (listState.firstVisibleItemScrollOffset / heroHeightPx).coerceIn(0f, 1f)
        }
    }
}
