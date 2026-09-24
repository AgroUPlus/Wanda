package com.wander.android.ui.screens.queue

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.cancellation.CancellationException

/**
 * How far open the queue drawer is, 0 (gone) to 1 (fully up), moved directly by the finger.
 *
 * The drawer used to be a `ModalBottomSheet`, which can only be told to show or hide: the swipe
 * that opened it waited for a threshold and then the sheet appeared on its own, detached from the
 * gesture. Owning the position here is what lets the drawer ride the finger up from the player,
 * and back down again, and only settle — on a spring — once the finger lets go.
 */
@Stable
internal class QueueDrawerState {
    /**
     * Plain state, written synchronously by every drag. It used to be an `Animatable` moved with
     * `snapTo` from launched coroutines — which could still be queued when the finger lifted, run
     * after the settle had started, and cancel it, leaving the drawer parked halfway.
     */
    var progress by mutableFloatStateOf(0f)
        private set

    /** The drawer's full height, in px — set by the host, which knows it before the first drag. */
    var heightPx by mutableFloatStateOf(0f)

    private var settling by mutableStateOf(false)

    /** Bumped by every drag and every new animation; a settle that sees it move on stops. */
    private var generation = 0

    /** Composed at all only while some of it is on screen, or a settle is carrying it there. */
    val isVisible: Boolean get() = progress > 0f || settling

    /** [dy] in px, screen direction: negative opens, positive closes. Takes over from any settle. */
    fun dragBy(dy: Float) {
        if (heightPx <= 0f) return
        generation++
        progress = (progress - dy / heightPx).coerceIn(0f, 1f)
    }

    /** Puts the drawer at [value] at once — a back swipe's pose, or gone when the player closes. */
    fun snapTo(value: Float) {
        generation++
        progress = value.coerceIn(0f, 1f)
    }

    /**
     * Finishes a drag: a fling decides by its direction, a slow release by where the drawer is.
     * [velocityY] in px/s, screen direction.
     */
    suspend fun settle(velocityY: Float, spec: AnimationSpec<Float>) {
        val target = when {
            velocityY < -FlingVelocity -> 1f
            velocityY > FlingVelocity -> 0f
            progress > OpenThreshold -> 1f
            else -> 0f
        }
        val initial = if (heightPx > 0f) -velocityY / heightPx else 0f
        animateTo(target, spec, initial)
    }

    suspend fun open(spec: AnimationSpec<Float>) = animateTo(1f, spec)

    suspend fun close(spec: AnimationSpec<Float>) = animateTo(0f, spec)

    private suspend fun animateTo(target: Float, spec: AnimationSpec<Float>, velocity: Float = 0f) {
        val mine = ++generation
        settling = true
        try {
            animate(progress, target, velocity, spec) { value, _ ->
                if (generation != mine) throw Superseded
                progress = value
            }
        } catch (_: Superseded) {
            // A drag or a newer animation took over; it owns `progress` now.
        } finally {
            if (generation == mine) settling = false
        }
    }

    /** Thrown only inside [animateTo], and caught there — never reaches a caller. */
    private object Superseded : CancellationException("queue drawer superseded") {
        private fun readResolve(): Any = Superseded
    }

    private companion object {
        const val FlingVelocity = 1200f
        const val OpenThreshold = 0.4f
    }
}

@Composable
internal fun rememberQueueDrawerState(): QueueDrawerState = remember { QueueDrawerState() }

/**
 * Lets the list and the drawer share one drag: pulling up on a drawer that is not fully open opens
 * it before the list scrolls, and pulling down past the top of the list carries the drawer down
 * with the finger. A fling that ends a partial drag settles the drawer instead of the list.
 */
internal fun drawerNestedScroll(
    drawer: QueueDrawerState,
    spec: () -> AnimationSpec<Float>
) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y < 0f && drawer.progress < 1f) {
            drawer.dragBy(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y > 0f && source == NestedScrollSource.UserInput) {
            drawer.dragBy(available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (drawer.progress < 1f) {
            drawer.settle(available.y, spec())
            return available
        }
        return Velocity.Zero
    }
}

/** How far down a full back swipe pulls the drawer before it commits. */
internal const val BackPeek = 0.35f

/** How dark the player gets behind a fully open drawer. */
internal const val ScrimAlpha = 0.4f

/** Tall enough to be the queue, short enough that the player is still visibly behind it. */
internal const val QueueDrawerHeightFraction = 0.82f

