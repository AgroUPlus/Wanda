package com.wander.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollHomeJumpTargetTest {

    @Test
    fun `a list near the top animates the whole way`() {
        assertNull(scrollHomeJumpTarget(0))
        assertNull(scrollHomeJumpTarget(SCROLL_HOME_JUMP_INDEX))
    }

    @Test
    fun `a list far down jumps to the last stretch first`() {
        assertEquals(SCROLL_HOME_JUMP_INDEX, scrollHomeJumpTarget(SCROLL_HOME_JUMP_INDEX + 1))
        assertEquals(SCROLL_HOME_JUMP_INDEX, scrollHomeJumpTarget(5_000))
    }
}
