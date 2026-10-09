package com.wander.android.ui.screens.home.customize

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Turns a still tap anywhere on the shelf into [onTap], and keeps it from reaching the shelf's own
 * cards. The shelves inside the customizer are the real ones, whose taps start radio; here a tap
 * means "edit this shelf".
 *
 * It listens before the children do (the initial pass) and consumes only a release that did not
 * move and did not outlast a long press, so scrolling the shelf sideways and holding it to drag
 * both pass through untouched.
 */
internal fun Modifier.shelfTap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    val slop = viewConfiguration.touchSlop
    val holdMillis = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var moved = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                .firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > slop) moved = true
            if (change.changedToUp()) {
                if (!moved && change.uptimeMillis - down.uptimeMillis < holdMillis) {
                    change.consume()
                    onTap()
                }
                break
            }
        }
    }
}

/**
 * Cards in a shelf open their own menu on a long press. Inside the customizer a long press means
 * "pick this shelf up", so the shelf's content is given a view configuration whose long press
 * never arrives; the drag handle around it keeps the normal one.
 */
@Composable
internal fun rememberHeldNeverConfiguration(): ViewConfiguration {
    val base = LocalViewConfiguration.current
    return remember(base) {
        object : ViewConfiguration by base {
            override val longPressTimeoutMillis: Long get() = Long.MAX_VALUE
        }
    }
}

