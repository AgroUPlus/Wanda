package com.wander.android.ui.screens.welcome

/**
 * The gestures the player answers to, as the setup screen teaches them.
 *
 * Every one of these is a real gesture with a handler behind it — `swipeUpToOpenQueue`,
 * `rememberSwipeToChangeTrack`, and the cover's own `detectTapGestures`. This list is a
 * description of that behaviour and has to be changed with it; a tutorial that teaches a gesture
 * the player no longer has is worse than no tutorial.
 *
 * Each carries the [motion] its demonstration traces, which is what [GesturePreview] animates.
 */
internal enum class PlayerGesture(
    val title: String,
    val detail: String,
    val motion: GestureMotion
) {
    OPEN_QUEUE(
        title = "Swipe up for the queue",
        detail = "From anywhere on the player — no handle to aim for.",
        motion = GestureMotion.SWIPE_UP
    ),
    SPEED_PITCH(
        title = "Hold the cover for speed & pitch",
        detail = "Press and hold the artwork to slow a track down or tune it.",
        motion = GestureMotion.HOLD
    ),
    LYRICS(
        title = "Tap the cover for lyrics",
        detail = "Tap again to bring the artwork back.",
        motion = GestureMotion.TAP
    ),
    SKIP(
        title = "Swipe the cover to skip",
        detail = "Left for the next track, right for the previous one.",
        motion = GestureMotion.SWIPE_SIDEWAYS
    ),
    COLLAPSE(
        title = "Swipe down to tuck it away",
        detail = "The player shrinks to the strip above the tabs and keeps playing.",
        motion = GestureMotion.SWIPE_DOWN
    )
}

/** The shape of a demonstration: where the cursor goes, and whether it presses. */
internal enum class GestureMotion { SWIPE_UP, SWIPE_DOWN, SWIPE_SIDEWAYS, TAP, HOLD }
