package com.wander.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The small vibrations, as a vocabulary rather than as calls to a vibrator.
 *
 * Named for what happened, not for how it should feel. `haptics.toggled(on)` says a switch flipped;
 * whether that is one tick or two, and how sharp, is the platform's answer and it differs by device
 * and by Android version. Spelling out durations and amplitudes here would override a system that
 * knows more about the hardware than this app does — and would ignore the user's own haptic
 * settings, which is how an app ends up buzzing at someone who turned that off.
 *
 * ## What deliberately does *not* vibrate
 *
 * Ordinary taps: navigating, opening a screen, scrolling, tapping a track to play it. Feedback on
 * everything is feedback on nothing, and it is the fastest way to make a phone feel cheap. What is
 * left is the small set where the touch and the result are not obviously the same event — a toggle
 * whose new state you have to look at to confirm, a value you released at a position, a link that
 * took seconds to form. Those are the moments a vibration answers a question rather than repeating
 * one.
 */
@Immutable
internal class Haptics(private val feedback: HapticFeedback) {

    /** A switch, a like, a play/pause — something that is now in a different state. */
    fun toggled(on: Boolean) = feedback.performHapticFeedback(
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
    )

    /** An action taken that will now go and do something: connect, share, start a measurement. */
    fun confirmed() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A dragged value let go at a position — the seek bar, chiefly. */
    fun settled() = feedback.performHapticFeedback(HapticFeedbackType.GestureEnd)

    /** A press held long enough to mean something. */
    fun heldDown() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
internal fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
