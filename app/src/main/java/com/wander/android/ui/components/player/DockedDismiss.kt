package com.wander.android.ui.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wander.android.ui.components.rubberBand

/**
 * Swiping the resting docked strip down to put the player away.
 *
 * [isAllowed] is asked once, when a finger lands on the strip: a jam follower cannot pause, so a
 * dismiss there would hide a player that keeps playing. [onDismiss] is called the moment the
 * gesture commits; the caller pauses and hides the strip, and the strip's own slide-out is
 * already running by then.
 */
class DockedDismiss(
    val isAllowed: () -> Boolean,
    val onDismiss: () -> Unit
)

/**
 * Finger travel past the resting strip that commits a dismiss on release. Measured on the raw
 * finger, not the damped strip: [rubberBand] at [OverdragDistance] puts the strip about 38 dp below
 * rest at this point, far enough to read as "this is going somewhere" before letting go.
 */
internal val DockedDismissDistance: Dp = 96.dp

/**
 * The least pull that a downward fling still commits on. A flick needs some travel behind it, or
 * a tap that slips a few pixels on its way up would close the player.
 */
internal val DockedDismissFlingDistance: Dp = 16.dp

/** Whether a rest-started pull of [pullPx], released at [velocity], should dismiss the strip. */
internal fun commitsDockedDismiss(
    pullPx: Float,
    velocity: Float,
    distancePx: Float,
    flingDistancePx: Float
): Boolean = pullPx >= distancePx ||
    (velocity > PlayerSheetState.FLING_VELOCITY && pullPx >= flingDistancePx)

/**
 * Places the sheet for a gesture that started on the resting strip, from the finger's whole
 * travel since it landed rather than frame by frame.
 *
 * [PlayerSheetState.dragBy] damps each frame's delta on top of the already damped offset, so how
 * far it gives depends on how fast the finger moves rather than how far — fine for the little
 * extra give at the end of a collapse, but a dismiss threshold has to mean the same distance
 * every time. Upward travel tracks the finger 1:1, exactly as [PlayerSheetState.dragBy] does.
 */
internal suspend fun PlayerSheetState.pullDocked(pullPx: Float, overdragLimitPx: Float) {
    if (maxOffsetPx <= 0f) return
    val newOffset = if (pullPx > 0f) {
        maxOffsetPx + rubberBand(pullPx, overdragLimitPx)
    } else {
        (maxOffsetPx + pullPx).coerceAtLeast(0f)
    }
    offset.snapTo(newOffset)
}

/**
 * Carries a committed dismiss on down out of view, keeping the release velocity so the strip
 * leaves at the speed it was thrown. [targetPx] is the sheet's whole height: the strip's top edge
 * rests at [PlayerSheetState.maxOffsetPx], so translating the sheet that far clears it for sure.
 */
internal suspend fun PlayerSheetState.slideOutDocked(
    targetPx: Float,
    velocity: Float,
    spec: AnimationSpec<Float>
) {
    targetValue = PlayerSheetValue.COLLAPSED
    offset.animateTo(targetPx, spec, initialVelocity = velocity.coerceAtLeast(0f))
}

/**
 * Brings a strip that was slid out back to its resting place — for an undo that lands while the
 * exit is still running, when the sheet never left composition and nothing else would reset it.
 */
internal suspend fun PlayerSheetState.returnToDock(spec: AnimationSpec<Float>) {
    if (maxOffsetPx <= 0f || offset.value <= maxOffsetPx) return
    offset.animateTo(maxOffsetPx, spec)
}
