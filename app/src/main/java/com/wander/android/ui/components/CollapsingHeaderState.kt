package com.wander.android.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize

/**
 * The hand-off of a page's title from its hero into [CompactHeroTopBar]: the hero's title scrolls
 * up with the page, and over the last [collapseDistancePx] before it reaches the bar it slides
 * sideways and shrinks into the bar's title slot, where it then stays — one title that moves,
 * rather than a second one fading in out of nowhere.
 *
 * Both ends report where they are ([heroTitleBounds] via [collapsingTitleSource], [slotBounds]
 * from the bar) in root coordinates, so only their difference matters and neither has to know
 * where the other sits in the tree. The hero's title is hidden the moment the hand-off starts; from
 * then on the bar draws the travelling copy.
 *
 * [fraction] purely follows the list's scroll offset — nothing here ever animates the scroll
 * position itself. It used to also spring the list to whichever end of the hand-off was nearer
 * the moment a drag or fling settled, so letting go a hair into the transition yanked the page the
 * rest of the way there (or back) on its own. That reads as the title moving without being asked
 * to; removing it means the title's frame changes *only* while [fraction] is strictly between 0
 * and 1 — actually being dragged through the hand-off — and sits wherever the scroll left it the
 * rest of the time, same as everything else on the page.
 */
@Stable
class CollapsingTitleState internal constructor(private val listState: LazyListState) {
    var heroTitleBounds by mutableStateOf<Rect?>(null)
        internal set
    var slotBounds by mutableStateOf<Rect?>(null)
        internal set

    /** How far above the bar the hand-off starts; set by the bar, which knows its density. */
    internal var collapseDistancePx by mutableFloatStateOf(0f)

    /** 0 while the hero's title is still itself, 1 once it is docked in the bar. */
    val fraction: Float by derivedStateOf {
        val hero = heroTitleBounds
        val slot = slotBounds
        when {
            // The hero item has scrolled off and been disposed; its last bounds are stale.
            listState.firstVisibleItemIndex > 0 -> 1f
            hero == null || slot == null || collapseDistancePx <= 0f -> 0f
            else -> {
                val start = slot.center.y + collapseDistancePx
                ((start - hero.center.y) / collapseDistancePx).coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Where the travelling title's centre is on screen: following the hero's title while it
     * scrolls, pinned to the slot once it gets there.
     */
    internal fun travellingCenterY(): Float {
        val slot = slotBounds ?: return 0f
        val hero = heroTitleBounds
        if (hero == null || listState.firstVisibleItemIndex > 0) return slot.center.y
        return maxOf(hero.center.y, slot.center.y)
    }
}

@Composable
fun rememberCollapsingTitleState(listState: LazyListState): CollapsingTitleState =
    remember(listState) { CollapsingTitleState(listState) }

/**
 * Marks the hero's own title: reports its bounds to [state] and hides it once the bar has taken
 * over drawing it, so the two copies are never on screen at once.
 */
fun Modifier.collapsingTitleSource(state: CollapsingTitleState): Modifier = this
    // Unclipped on purpose: `boundsInRoot` shrinks as the list clips the title at its top edge,
    // which would drag the measured centre along with it.
    .onGloballyPositioned { state.heroTitleBounds = Rect(it.positionInRoot(), it.size.toSize()) }
    .graphicsLayer { alpha = if (state.fraction > 0f) 0f else 1f }
