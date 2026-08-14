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
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) {
    var targetValue by mutableStateOf(initialValue)
        internal set

    var maxOffsetPx by mutableFloatStateOf(0f)
        internal set

    internal val offset = Animatable(if (initialValue == PlayerSheetValue.EXPANDED) 0f else Float.MAX_VALUE)

    val progress: Float by derivedStateOf {
        if (maxOffsetPx <= 0f) {
            if (targetValue == PlayerSheetValue.EXPANDED) 1f else 0f
        } else {
            (1f - (offset.value / maxOffsetPx)).coerceIn(0f, 1f)
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
    return rememberSaveable(saver = PlayerSheetState.Saver) {
        PlayerSheetState(initialValue)
    }
}
