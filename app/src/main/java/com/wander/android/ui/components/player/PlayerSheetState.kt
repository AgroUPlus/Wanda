package com.wander.android.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    private val animationSpec: AnimationSpec<Float> = spring(
        dampingRatio = ExpressiveDamping,
        stiffness = Spring.StiffnessMediumLow
    )
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
     * see. Only the travelling artwork reads this, where overshooting past the final frame is the
     * whole effect; the lower bound is still held at 0 because a sheet that reads as less than
     * closed has nothing to show.
     */
    internal val rawProgress: Float by derivedStateOf {
        if (maxOffsetPx <= 0f) {
            if (targetValue == PlayerSheetValue.EXPANDED) 1f else 0f
        } else {
            (1f - (offset.value / maxOffsetPx)).coerceAtLeast(0f)
        }
    }

    val isExpanded: Boolean get() = progress > 0.5f

    internal suspend fun updateMaxOffset(newMaxOffset: Float) {
        if (newMaxOffset <= 0f) return
        val isFirstMeasure = maxOffsetPx <= 0f
        maxOffsetPx = newMaxOffset
        if (isFirstMeasure || offset.value > newMaxOffset) {
            if (targetValue == PlayerSheetValue.EXPANDED) {
                offset.snapTo(0f)
            } else {
                offset.snapTo(newMaxOffset)
            }
        }
    }

    suspend fun expand() {
        targetValue = PlayerSheetValue.EXPANDED
        if (offset.value == 0f) return
        offset.animateTo(0f, animationSpec)
    }

    suspend fun collapse() {
        targetValue = PlayerSheetValue.COLLAPSED
        if (maxOffsetPx > 0f) {
            offset.animateTo(maxOffsetPx, animationSpec)
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

        /**
         * A hint of overshoot, and no more.
         *
         * Between `DampingRatioLowBouncy` (0.55) and `DampingRatioNoBouncy` (1.0). This started at
         * 0.75 and was too loose in the hand: the player is a full-screen surface with the cover
         * art riding on it, so an overshoot that reads as playful on a small chip reads as the
         * sheet wobbling. Big surfaces want less bounce than small ones.
         */
        const val ExpressiveDamping = 0.9f

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
    return rememberSaveable(saver = PlayerSheetState.Saver) {
        PlayerSheetState(initialValue)
    }
}
