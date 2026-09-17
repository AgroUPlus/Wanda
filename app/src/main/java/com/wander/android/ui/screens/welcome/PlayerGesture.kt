package com.wander.android.ui.screens.welcome

import androidx.annotation.StringRes
import com.wander.android.R

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
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val motion: GestureMotion
) {
    OPEN_QUEUE(
        title = R.string.welcome_gesture_swipe_up_queue,
        detail = R.string.welcome_gesture_detail_from_anywhere_player_no_handle,
        motion = GestureMotion.SWIPE_UP
    ),
    SPEED_PITCH(
        title = R.string.welcome_gesture_hold_cover_speed_pitch,
        detail = R.string.welcome_gesture_detail_press_hold_artwork_slow_track,
        motion = GestureMotion.HOLD
    ),
    LYRICS(
        title = R.string.welcome_gesture_tap_cover_lyrics,
        detail = R.string.welcome_gesture_detail_tap_again_bring_artwork_back,
        motion = GestureMotion.TAP
    ),
    SKIP(
        title = R.string.welcome_gesture_swipe_cover_skip,
        detail = R.string.welcome_gesture_detail_left_next_track_right_previous,
        motion = GestureMotion.SWIPE_SIDEWAYS
    ),
    COLLAPSE(
        title = R.string.welcome_gesture_swipe_down_tuck_away,
        detail = R.string.welcome_gesture_detail_player_shrinks_strip_above_tabs,
        motion = GestureMotion.SWIPE_DOWN
    )
}

/** The shape of a demonstration: where the cursor goes, and whether it presses. */
internal enum class GestureMotion { SWIPE_UP, SWIPE_DOWN, SWIPE_SIDEWAYS, TAP, HOLD }
