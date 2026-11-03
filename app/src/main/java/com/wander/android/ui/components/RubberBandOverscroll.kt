package com.wander.android.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sign

/**
 * A vertical list's overscroll as a rubber band: dragged past its first or last item, the content
 * follows the finger with growing resistance ([rubberBand]) and springs back on release, carrying
 * the release or fling velocity — instead of the platform's stretch, which distorts the rows
 * rather than moving them.
 *
 * Only what nothing else wanted reaches the band. The scrollable hands [applyToScroll] a
 * `performScroll` that already includes nested-scroll pre- and post-dispatch, so a parent that
 * takes the leftover (the queue drawer pulling itself down at the top of its list) gets it first,
 * and the band only ever sees what the parent left.
 */
@Composable
fun rememberRubberBandOverscroll(): OverscrollEffect {
    val spec by rememberUpdatedState(MaterialTheme.motionScheme.defaultSpatialSpec<Float>())
    return remember { RubberBandOverscroll { spec } }
}

/** See [rememberRubberBandOverscroll]. Vertical only: horizontal deltas pass straight through. */
internal class RubberBandOverscroll(
    private val spec: () -> AnimationSpec<Float>
) : OverscrollEffect {
    /**
     * Undamped pull past the edge in px, screen direction: positive has the content moved down
     * (pulled past the top), negative moved up (past the bottom). Damped only when drawn, so a
     * finger that reverses unwinds it 1:1 and the band is slack again exactly where it began.
     */
    var displacement by mutableFloatStateOf(0f)
        private set

    /** Bumped by every touch and every spring; a spring that sees it move on stops. */
    private var generation = 0

    override val isInProgress: Boolean get() = displacement != 0f

    override val node: DelegatableNode = RubberBandNode(this)

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        // A fling's own frames never stretch the band; the velocity it ends with does, in
        // applyToFling. Letting them through unconsumed is also what tells the fling it hit the edge.
        if (source != NestedScrollSource.UserInput) return performScroll(delta)
        generation++

        // Moving back toward the content unwinds the band before anything scrolls, so the list
        // (and its parent) only move again once the band is slack.
        val y = delta.y
        val relax = if (displacement != 0f && sign(y) == -sign(displacement)) {
            if (abs(y) >= abs(displacement)) -displacement else y
        } else {
            0f
        }
        displacement += relax

        val rest = Offset(delta.x, y - relax)
        val consumed = performScroll(rest)
        val leftover = rest.y - consumed.y
        displacement += leftover
        return Offset(consumed.x, consumed.y + relax + leftover)
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        // Released while stretched: the spring back takes the release velocity, and whatever it
        // still carries as it reaches the edge goes on into the list as a fling.
        val handOff = if (displacement != 0f) springBack(velocity.y) ?: return else velocity.y
        val left = performFling(Velocity(velocity.x, handOff))
        // The list ran into its edge mid-fling: the rest of that velocity bounces the band.
        if (left.y != 0f) springBack(left.y)
    }

    /**
     * Springs [displacement] back to rest from [initialVelocity] (px/s, screen direction), and
     * stops at the edge rather than overshooting into the opposite one. Returns the velocity it
     * still had at the edge (0 if it settled there), or null if a touch took over first.
     */
    private suspend fun springBack(initialVelocity: Float): Float? {
        val mine = ++generation
        val side = if (displacement != 0f) sign(displacement) else sign(initialVelocity)
        var atEdge = 0f
        try {
            animate(displacement, 0f, initialVelocity, spec()) { value, speed ->
                if (generation != mine) throw Superseded
                if (sign(value) == -side) {
                    atEdge = speed
                    displacement = 0f
                    throw Crossed
                }
                displacement = value
            }
        } catch (_: Crossed) {
            // Reached the edge still moving; the caller hands that on.
        } catch (_: Superseded) {
            return null
        }
        return atEdge
    }

    /** Thrown only inside [springBack], and caught there — never reaches a caller. */
    private object Superseded : CancellationException("rubber band superseded") {
        private fun readResolve(): Any = Superseded
    }

    /** Thrown only inside [springBack], and caught there — never reaches a caller. */
    private object Crossed : CancellationException("rubber band reached the edge") {
        private fun readResolve(): Any = Crossed
    }
}

/**
 * Moves the content by the damped band in a placement layer: the offset is read only in the layer
 * block, so a pull or a spring redraws the layer each frame without recomposing or re-measuring
 * the list. A layer rather than a draw translation so touches land on the rows where they are drawn.
 */
private class RubberBandNode(private val effect: RubberBandOverscroll) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationY = rubberBand(effect.displacement, size.height * STRETCH_LIMIT)
            }
        }
    }
}

/** The furthest the band can carry the content, as a share of the list's height — never reached. */
private const val STRETCH_LIMIT = 0.3f
