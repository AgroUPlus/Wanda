package com.wander.android.ui.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavBackStackEntry
import java.lang.ref.WeakReference

/**
 * Which destination the last finger came down on, for [navigateSettled] to tell a tap on the
 * screen arriving from one on the screen still leaving. Null when it came down outside every
 * destination: the dock, the rail, the player.
 *
 * Weak, so a popped entry is not kept alive by the last touch it took.
 */
internal object TouchOrigin {
    private var last: WeakReference<NavBackStackEntry>? = null

    val entry: NavBackStackEntry? get() = last?.get()

    fun set(entry: NavBackStackEntry?) {
        last = entry?.let(::WeakReference)
    }
}

/**
 * Notes every finger-down that reaches here as coming from [entry]. On the initial pass, so it is
 * seen before anything inside handles it, and never consumed. The shell's root records null first
 * ([clearsTouchOrigin]); a destination's own record, deeper in the same pass, then overrides it.
 */
internal fun Modifier.recordsTouchOrigin(entry: NavBackStackEntry): Modifier = pointerInput(entry) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) TouchOrigin.set(entry)
        }
    }
}

/** On the shell's root: a finger that lands on no destination came from none. */
internal fun Modifier.clearsTouchOrigin(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) TouchOrigin.set(null)
        }
    }
}
