package com.wander.android.ui.components

import androidx.compose.animation.core.snap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RubberBandOverscrollTest {
    private val effect = RubberBandOverscroll { snap() }

    /** A list that can scroll [room] px further down-the-content (negative deltas) and none back. */
    private fun atTopWith(room: Float): (Offset) -> Offset {
        var left = room
        return { delta ->
            val taken = if (delta.y < 0f) maxOf(delta.y, -left) else 0f
            left += taken
            Offset(0f, taken)
        }
    }

    private fun drag(dy: Float, performScroll: (Offset) -> Offset) =
        effect.applyToScroll(Offset(0f, dy), NestedScrollSource.UserInput, performScroll)

    @Test
    fun `a pull past the edge stretches the band by what the list could not take`() {
        val consumed = drag(40f, atTopWith(0f))
        assertEquals(40f, consumed.y, 0f)
        assertEquals(40f, effect.displacement, 0f)
        assertTrue(effect.isInProgress)
    }

    @Test
    fun `scrolling away from a stretched edge unwinds the band before the list moves`() {
        drag(30f, atTopWith(0f))
        var listSaw = 0f
        drag(-50f) { delta -> listSaw = delta.y; delta }
        assertEquals(-20f, listSaw, 0f)
        assertEquals(0f, effect.displacement, 0f)
        assertFalse(effect.isInProgress)
    }

    @Test
    fun `whatever a parent takes in post-scroll never reaches the band`() {
        // The queue drawer: a downward pull past the top is all consumed by the drawer.
        val consumed = drag(40f) { delta -> delta }
        assertEquals(40f, consumed.y, 0f)
        assertEquals(0f, effect.displacement, 0f)
    }

    @Test
    fun `fling frames pass through without stretching`() {
        val consumed = effect.applyToScroll(Offset(0f, 40f), NestedScrollSource.SideEffect, atTopWith(0f))
        assertEquals(0f, consumed.y, 0f)
        assertEquals(0f, effect.displacement, 0f)
    }
}
