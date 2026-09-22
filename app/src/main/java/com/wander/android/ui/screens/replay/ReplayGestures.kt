package com.wander.android.ui.screens.replay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How the story is driven: tap a side to move, hold anywhere to pause.
 *
 * Written against the raw pointer stream rather than `detectTapGestures`, because the *hold* has to
 * take effect the instant a finger lands. A long-press detector has a threshold, so half a second
 * of the card would still slide past under the thumb of somebody who put their finger down
 * precisely to stop it.
 *
 * The same press also cancels the advance: lifting counts as a tap only if the card did not change
 * while the finger was down, so pausing on a card and letting go does not then skip it.
 */
internal fun Modifier.replayTapAndHold(
    onPreviousTap: () -> Unit,
    onNextTap: () -> Unit,
    onHoldChange: (Boolean) -> Unit
): Modifier = pointerInput(onPreviousTap, onNextTap, onHoldChange) {
    val backWidth = size.width * BackFraction

    awaitEachGesture {
        val down = awaitFirstDown()
        onHoldChange(true)

        val up = try {
            waitForUpOrCancellation()
        } finally {
            // In a `finally` so a cancelled gesture — a parent taking over the pointer, the screen
            // going away mid-press — cannot leave the story paused forever with nothing to resume
            // it.
            onHoldChange(false)
        }

        if (up != null) {
            if (down.position.x < backWidth) onPreviousTap() else onNextTap()
        }
    }
}

/**
 * The left third goes back, the rest goes forward.
 *
 * Not half and half: forward is the overwhelmingly common intent, and a centre tap should advance
 * rather than rewind.
 */
private const val BackFraction = 0.33f
