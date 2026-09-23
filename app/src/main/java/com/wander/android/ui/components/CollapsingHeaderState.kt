package com.wander.android.ui.components

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
     * How far to scroll so the page rests at one end of the header's range — fully unscrolled, or
     * scrolled exactly far enough that the title is docked in the bar — whichever is closer; null
     * when it already rests at one. The whole stretch between the two is a transition, never a
     * place to stop: that is what makes the header behave like one control rather than a picture
     * the list happens to start with.
     */
    internal fun snapDelta(): Float? {
        if (listState.firstVisibleItemIndex > 0) return null
        val hero = heroTitleBounds ?: return null
        val slot = slotBounds ?: return null
        val scrolled = listState.firstVisibleItemScrollOffset.toFloat()
        val toDock = hero.center.y - slot.center.y
        if (scrolled <= 0f || toDock <= 0f) return null
        return if (scrolled < (scrolled + toDock) / 2f) -scrolled else toDock
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
fun rememberCollapsingTitleState(listState: LazyListState): CollapsingTitleState {
    val state = remember(listState) { CollapsingTitleState(listState) }
    // Read through updated state rather than keyed on: the motion scheme hands out a fresh spec
    // object per call, and keying on it restarted this effect on every recomposition — which the
    // scroll itself causes — cancelling the snap halfway and leaving the title exactly where it
    // was not supposed to rest.
    val spec by rememberUpdatedState(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    // Once the finger lets go and any fling has run out, a title caught mid-way springs to the
    // nearer end. The snap is itself a scroll, and when it lands the title is at an end, so this
    // never chases its own tail.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { inProgress -> !inProgress }
            .collect {
                // Launched, not awaited: a finger landing mid-snap cancels the snap's scroll, and
                // that cancellation thrown straight out of `collect` ended this whole effect —
                // after the first interrupted snap, the title never snapped again. As a child
                // job only that one snap dies.
                state.snapDelta()?.let { delta -> launch { listState.animateScrollBy(delta, spec) } }
            }
    }
    return state
}

/**
 * Marks the hero's own title: reports its bounds to [state] and hides it once the bar has taken
 * over drawing it, so the two copies are never on screen at once.
 */
fun Modifier.collapsingTitleSource(state: CollapsingTitleState): Modifier = this
    // Unclipped on purpose: `boundsInRoot` shrinks as the list clips the title at its top edge,
    // which would drag the measured centre along with it.
    .onGloballyPositioned { state.heroTitleBounds = Rect(it.positionInRoot(), it.size.toSize()) }
    .graphicsLayer { alpha = if (state.fraction > 0f) 0f else 1f }
