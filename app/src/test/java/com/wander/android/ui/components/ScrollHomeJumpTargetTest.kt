package com.wander.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollHomeJumpTargetTest {

    @Test
    fun `a list near the top animates the whole way`() {
        assertNull(scrollHomeJumpTarget(0))
        assertNull(scrollHomeJumpTarget(ScrollHomeJumpIndex))
    }

    @Test
    fun `a list far down jumps to the last stretch first`() {
        assertEquals(ScrollHomeJumpIndex, scrollHomeJumpTarget(ScrollHomeJumpIndex + 1))
        assertEquals(ScrollHomeJumpIndex, scrollHomeJumpTarget(5_000))
    }
}
