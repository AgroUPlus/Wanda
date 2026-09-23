package com.wander.android.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.wander.android.data.model.UnifiedTrack
import com.wander.android.ui.theme.LocalReducedMotion

/**
 * The title and artist, carried along with the cover when the track changes: the outgoing text
 * slides away the way the filmstrip moves and fades, the incoming one arrives from the other side.
 * Direction comes from the queue index, so a skip back moves the other way.
 *
 * A quarter of the width, not the whole of it — the cover makes the big movement, and text that
 * travelled as far would read as a second thing sliding rather than the same change.
 */
@Composable
internal fun TrackTextTransition(
    track: UnifiedTrack,
    queueIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable (UnifiedTrack) -> Unit
) {
    val slide = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val reducedMotion = LocalReducedMotion.current

    AnimatedContent(
        targetState = TrackSlot(track, queueIndex),
        contentKey = { it.track.id },
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState.index >= initialState.index) 1 else -1
            val motion = if (reducedMotion) {
                fadeIn(fade) togetherWith fadeOut(fade)
            } else {
                (slideInHorizontally(slide) { it * direction / TravelDivisor } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(slide) { -it * direction / TravelDivisor } + fadeOut(fade))
            }
            motion using SizeTransform(clip = false)
        },
        label = "trackText"
    ) { slot -> content(slot.track) }
}

private data class TrackSlot(val track: UnifiedTrack, val index: Int)

private const val TravelDivisor = 4
