package com.wander.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints

/**
 * A [HorizontalPager] that morphs to the natural height of whichever page is showing, rather than
 * a fixed height sized for the tallest possible page (wasted space on the shorter one) or clipped
 * to the shortest (content cut off on the taller one, with an internal scroll standing in for
 * "auto size" and reading as broken — see `TrackInfoPage` and the buttons page it shares a pager
 * with in `TrackActionsSheet` / `NowPlayingMenuDrawer`).
 *
 * [HorizontalPager] needs a bounded height to lay its pages out, so simply reading a page's size
 * off `onSizeChanged` doesn't work: once the pager is given that measured height, the page is
 * re-measured *inside* it and reports the same (now-clamped) height straight back, chasing its own
 * tail instead of settling on the content's real size. A [SubcomposeLayout] probe measures each
 * page once under unbounded height, outside the pager's own bounded pass, so the probe measurement
 * never feeds back into itself. The pager then animates to whichever page is current as the user
 * swipes, rather than jumping straight to the new size.
 */
@Composable
internal fun MenuSheetPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    page: @Composable (page: Int) -> Unit
) {
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val unbounded = Constraints(maxWidth = constraints.maxWidth)
        val heightsPx = (0 until state.pageCount).map { index ->
            subcompose(MenuSheetPagerSlot.Probe(index)) { Box { page(index) } }
                .first()
                .measure(unbounded)
                .height
        }
        val targetHeight = heightsPx.getOrElse(state.currentPage) { heightsPx.maxOrNull() ?: 0 }.toDp()

        val pagerPlaceable = subcompose(MenuSheetPagerSlot.Pager) {
            val animatedHeight by animateDpAsState(targetValue = targetHeight, label = "menuSheetPagerHeight")
            HorizontalPager(
                state = state,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth().height(animatedHeight)
            ) { index -> page(index) }
        }.first().measure(unbounded)

        layout(pagerPlaceable.width, pagerPlaceable.height) {
            pagerPlaceable.place(0, 0)
        }
    }
}

private sealed class MenuSheetPagerSlot {
    data class Probe(val index: Int) : MenuSheetPagerSlot()
    data object Pager : MenuSheetPagerSlot()
}
