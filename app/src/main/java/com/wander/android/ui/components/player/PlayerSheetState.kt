package com.wander.android.ui.components.player

import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.core.animate
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.wander.android.ui.components.backResistance
import kotlin.math.abs

enum class PlayerSheetValue {
    COLLAPSED,
    EXPANDED
}

/**
 * Continuous bottom-sheet drag and expand/collapse state controller for the playback surface.
 */
@Stable
class PlayerSheetState(
    initialValue: PlayerSheetValue = PlayerSheetValue.COLLAPSED,
    private val animationSpec: AnimationSpec<Float> = MotionScheme.expressive().defaultSpatialSpec()
) {
    var targetValue by mutableStateOf(initialValue)
        internal set

    var maxOffsetPx by mutableFloatStateOf(0f)
        internal set

    internal val offset = Animatable(if (initialValue == PlayerSheetValue.EXPANDED) 0f else Float.MAX_VALUE)

    /**
     * How far open the sheet is, clamped to 0..1.
     *
     * Everything that would break outside that range reads this one: the corner radius goes
     * negative above 1, and the width/height lerp would report a box larger than the screen.
     */
    val progress: Float by derivedStateOf { rawProgress.coerceIn(0f, 1f) }

    /**
     * The same ratio with the spring's overshoot left in, so it can pass 1 at the end of a fling.
     *
     * The clamp above is what used to swallow the bounce entirely — the spring overshot the
     * *offset* and `progress` flattened it back to 1, so a bouncier spec changed nothing you could
     * see. Only the travelling cover *and the neighbours pitched off it* read this, where
     * overshooting past the final frame is the whole effect; the lower bound is still held at 0
     * because a sheet that reads as less than closed has nothing to show.
     *
     * The filmstrip has to ride the same value as the cover it is spaced around. Everything else —
     * the corner radius, the shadow, the reported box — stays on [progress] because it has nowhere
     * to overshoot *to*: each is already at its terminal value at 1, and a box reported larger
     * than the screen would break the layout rather than decorate it.
     */
    internal val rawProgress: Float by derivedStateOf {
        if (maxOffsetPx <= 0f) {
            if (targetValue == PlayerSheetValue.EXPANDED) 1f else 0f
        } else {
            (1f - (offset.value / maxOffsetPx)).coerceAtLeast(0f)
        }
    }

    val isExpanded: Boolean get() = progress > 0.5f

    /**
     * How far into a predictive-back gesture we are, 0..1 — purely visual, read only by
     * [PlayerSheet]'s shrink/shift treatment. Kept separate from [offset]/[progress], which
     * still drive the actual collapse (corner radius, height lerp) exactly as a manual drag does.
     */
    var predictiveBackProgress by mutableFloatStateOf(0f)
        internal set

    /**
     * The shrink/round/shift the back gesture draws on the sheet. Tracks [predictiveBackProgress]
     * during the gesture, then eases to 0 *with* the collapse or the cancel, so the sheet never
     * snaps from its shrunken pose back to full size in one frame.
     */
    var predictiveBackVisual by mutableFloatStateOf(0f)
        internal set

    /** Which edge the predictive-back gesture started from — decides which way the sheet shifts. */
    var predictiveBackSwipeEdge by mutableStateOf(BackEventCompat.EDGE_LEFT)
        internal set

    internal suspend fun updateMaxOffset(newMaxOffset: Float, scope: CoroutineScope) {
        if (newMaxOffset <= 0f) return
        val isFirstMeasure = maxOffsetPx <= 0f
        val wasCollapsed = targetValue == PlayerSheetValue.COLLAPSED
        val wasAtMax = abs(offset.value - maxOffsetPx) < 10f
        maxOffsetPx = newMaxOffset
        // A settle in flight owns the offset, and `snapTo` *cancels* the running `animateTo`.
        // The docked height is itself a spring, so every frame of it arrives here: a dock row
        // appearing while the sheet was still moving killed the expand partway and left the cover
        // stranded mid-overshoot. An offset that no longer fits the new travel is the one case
        // that must still be brought back into range, whatever is running.
        val outOfRange = offset.value > newMaxOffset
        if (offset.isRunning && !outOfRange) {
            // A collapse in flight is animating *toward* `maxOffsetPx` itself — `animateTo` below
            // captures it by value, so when a navigation collapses the sheet and drops the dock row
            // in the same beat, the running animation kept chasing the target it started with and
            // settled short, leaving the strip resting in the space the dock row used to reserve
            // until the row's own (slower) spring finished and snapped it in. Launched rather than
            // awaited so this collector keeps reading every frame of that spring instead of
            // blocking on one retarget at a time — `Animatable` serializes writes through its own
            // mutex, so each new launch simply preempts the last with its current velocity intact,
            // the same way `animateDpAsState` itself retargets.
            //
            // An expand in flight always heads for 0 regardless of `maxOffsetPx`, so it is left
            // alone here — retargeting it on every frame of the dock row's spring is what stranded
            // the cover mid-overshoot in the first place.
            if (wasCollapsed) {
                scope.launch { offset.animateTo(newMaxOffset, animationSpec) }
            }
            return
        }
        if (isFirstMeasure || wasCollapsed || wasAtMax || outOfRange) {
            if (targetValue == PlayerSheetValue.EXPANDED) {
                offset.snapTo(0f)
            } else {
                offset.snapTo(newMaxOffset)
            }
        }
    }

    suspend fun expand() {
        targetValue = PlayerSheetValue.EXPANDED
        coroutineScope {
            // A cancelled back swipe: the sheet grows back and the blur behind it returns together.
            launch { easeBackOut(clearBlur = true) }
            if (offset.value != 0f) offset.animateTo(0f, animationSpec)
        }
    }

    suspend fun collapse() {
        targetValue = PlayerSheetValue.COLLAPSED
        coroutineScope {
            // The blur keeps the value the gesture let go at — it is already mostly clear, and
            // it fades the rest of the way with the collapse's own progress.
            launch { easeBackOut(clearBlur = false) }
            if (maxOffsetPx > 0f) offset.animateTo(maxOffsetPx, animationSpec)
        }
        predictiveBackProgress = 0f
    }

    private suspend fun easeBackOut(clearBlur: Boolean) {
        val from = predictiveBackVisual
        val blurFrom = predictiveBackProgress
        if (from == 0f && (!clearBlur || blurFrom == 0f)) return
        animate(1f, 0f, animationSpec = animationSpec) { value, _ ->
            predictiveBackVisual = from * value
            if (clearBlur) predictiveBackProgress = blurFrom * value
        }
    }

    suspend fun snapToCollapsed() {
        targetValue = PlayerSheetValue.COLLAPSED
        if (maxOffsetPx > 0f) {
            offset.snapTo(maxOffsetPx)
        }
    }

    internal suspend fun dragBy(delta: Float) {
        if (maxOffsetPx <= 0f) return
        val newOffset = (offset.value + delta).coerceIn(0f, maxOffsetPx)
        offset.snapTo(newOffset)
    }

    /**
     * A back swipe on the full player, drawn the way the system draws one on a full-screen
     * surface: the sheet stays where it is and shrinks toward its centre with growing resistance
     * ([backResistance]), while the app behind comes into view. It used to also slide the sheet
     * down its travel at the same time, and the two movements together read as the player
     * falling apart. The collapse itself is the spring in [collapse] once the gesture commits.
     */
    internal fun updatePredictiveBackProgress(backProgress: Float, swipeEdge: Int) {
        val eased = backResistance(backProgress)
        predictiveBackProgress = eased
        predictiveBackVisual = eased
        predictiveBackSwipeEdge = swipeEdge
    }

    /**
     * Whether the player's back handler should be registered. Keyed on where the sheet is
     * *headed*, not on [progress]: the handler was keyed on [isExpanded], so a gesture that moved
     * the sheet past halfway disabled its own handler mid-swipe — on Home, with nothing else to
     * take the gesture, the sheet was left frozen there.
     */
    val isBackHandlerEnabled: Boolean get() = targetValue == PlayerSheetValue.EXPANDED

    internal suspend fun settle(velocity: Float) {
        if (maxOffsetPx <= 0f) return
        val target = when {
            velocity < -FLING_VELOCITY -> 0f
            velocity > FLING_VELOCITY -> maxOffsetPx
            progress > 0.5f -> 0f
            else -> maxOffsetPx
        }
        targetValue = if (target == 0f) PlayerSheetValue.EXPANDED else PlayerSheetValue.COLLAPSED
        // A gesture that pushes further into an anchor the sheet is already resting on has
        // nowhere to go. `dragBy` clamps it, so nothing moves under the finger — but springing
        // to an offset it already holds still lets the overshoot carry it *past* the anchor, and
        // the whole sheet lifted off the top of the screen for a frame or two on release. There
        // is no travel to animate, so there is nothing to animate.
        if (offset.value == target) return
        offset.animateTo(target, animationSpec, initialVelocity = velocity)
    }

    companion object {
        const val FLING_VELOCITY = 1000f


        val Saver: Saver<PlayerSheetState, PlayerSheetValue> = Saver(
            save = { it.targetValue },
            restore = { PlayerSheetState(it) }
        )
    }
}

@Composable
fun rememberPlayerSheetState(
    initialValue: PlayerSheetValue = PlayerSheetValue.COLLAPSED
): PlayerSheetState {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val saver = androidx.compose.runtime.remember(spec) {
        Saver<PlayerSheetState, PlayerSheetValue>(
            save = { it.targetValue },
            restore = { PlayerSheetState(it, spec) }
        )
    }
    return rememberSaveable(saver = saver) {
        PlayerSheetState(initialValue, spec)
    }
}
