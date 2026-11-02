package com.wander.android.ui.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Slides the filmstrip for a track change nobody swiped — a skip button, or a song running out —
 * so it moves exactly as a swipe's hand-off does.
 *
 * ## Why this is decided in composition
 *
 * The first version reacted in a `LaunchedEffect`, which runs after the frame that already drew
 * the new cover sitting in the slot. It then jumped the carriage one slot over and slid back — the
 * incoming cover flashed in place for a frame before its slide began. Here the decision is made
 * while composing that same frame, and the arrival [Animatable] is *created* at its starting
 * offset, so the first frame showing the new track already has it one slot out, with the outgoing
 * cover (now its neighbour) still in the slot.
 *
 * Only a step of one either way, only when [enabled], and never for a change a swipe made — that
 * one already landed the filmstrip itself and left [TrackSwipeState.pendingArtworkUrl] set.
 */
@Composable
internal fun TrackSwipeState.rememberTrackArrival(
    trackId: String?,
    index: Int,
    enabled: Boolean,
    spec: AnimationSpec<Float>
) {
    val last = remember { LastTrack(index) }
    val start = remember(trackId) {
        val step = index - last.index
        last.index = index
        val swiped = pendingArtworkUrl != null
        if (!enabled || swiped || isSwiping || stepPx <= 0f || (step != 1 && step != -1)) {
            0f
        } else {
            // Written before the artwork below reads it in this same composition, so the peeks
            // are composed on the first frame too — not a frame later with a neighbour missing.
            isSwiping = true
            // Forward: the new cover starts to the right and the carriage slides left.
            if (step == 1) stepPx else -stepPx
        }
    }
    val slide = remember(trackId) { Animatable(start) }
    arrival = slide

    LaunchedEffect(slide) {
        if (start == 0f) return@LaunchedEffect
        try {
            slide.animateTo(0f, spec)
        } finally {
            // A swipe starting mid-arrival cancels this; the peek covers must not be stranded.
            isSwiping = false
        }
    }
}

/** The index last seen, kept across recompositions without being state. */
private class LastTrack(var index: Int)
