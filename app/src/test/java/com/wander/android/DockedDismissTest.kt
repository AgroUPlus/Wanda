package com.wander.android

import com.wander.android.ui.components.player.PlayerSheetState
import com.wander.android.ui.components.player.commitsDockedDismiss
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a pull on the resting mini player puts it away, and when it only springs back. */
class DockedDismissTest {

    private val distance = 300f
    private val flingDistance = 50f
    private val fling = PlayerSheetState.FLING_VELOCITY + 1f

    private fun commits(pull: Float, velocity: Float) =
        commitsDockedDismiss(pull, velocity, distance, flingDistance)

    @Test
    fun `a slow pull past the distance commits`() {
        assertTrue(commits(pull = distance, velocity = 0f))
    }

    @Test
    fun `a slow pull short of the distance springs back`() {
        assertFalse(commits(pull = distance - 1f, velocity = 0f))
    }

    @Test
    fun `a downward fling with some travel behind it commits`() {
        assertTrue(commits(pull = flingDistance, velocity = fling))
    }

    @Test
    fun `a fling that barely moved springs back`() {
        assertFalse(commits(pull = flingDistance - 1f, velocity = fling))
    }

    @Test
    fun `an upward fling never commits`() {
        assertFalse(commits(pull = flingDistance, velocity = -fling))
    }
}
