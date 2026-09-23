package com.wander.android.ui.components

import kotlin.math.abs
import kotlin.math.sign

/**
 * The "pulls against you" feel shared by every custom drag in the app, modelled on the system's
 * predictive-back arrow: early movement follows the finger closely, and each further centimetre
 * moves the surface less. Paired with a spring on release (the motion scheme's spatial specs) this
 * is what reads as bouncy rather than dead.
 */

/**
 * Eases a 0..1 predictive-back progress so it resists as it grows — a quadratic ease-out, fast
 * off the edge and slowing toward the end.
 */
fun backResistance(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p)
}

/**
 * Maps a raw finger displacement to a damped one that approaches (but never reaches) [limit],
 * the classic rubber band: at small displacements it tracks the finger 1:1, and it stiffens the
 * further it is pulled. Sign is preserved, so it works for either direction.
 */
fun rubberBand(displacement: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val magnitude = abs(displacement)
    val damped = limit * (1f - 1f / (magnitude / limit + 1f))
    return damped * sign(displacement)
}
