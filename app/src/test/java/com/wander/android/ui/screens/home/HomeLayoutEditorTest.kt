package com.wander.android.ui.screens.home

import com.wander.android.ui.screens.home.layout.HomeLayoutEditor
import com.wander.android.ui.screens.home.layout.ShelfConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeLayoutEditorTest {

    private val configs = listOf(ShelfConfig("a"), ShelfConfig("b"), ShelfConfig("c"), ShelfConfig("d"))

    private fun ids(list: List<ShelfConfig>) = list.map { it.id }

    @Test
    fun `move down lands on the target position`() {
        assertEquals(listOf("b", "c", "a", "d"), ids(HomeLayoutEditor.move(configs, "a", "c")))
    }

    @Test
    fun `move up lands on the target position`() {
        assertEquals(listOf("a", "d", "b", "c"), ids(HomeLayoutEditor.move(configs, "d", "b")))
    }

    @Test
    fun `unknown ids and self moves change nothing`() {
        assertSame(configs, HomeLayoutEditor.move(configs, "a", "zzz"))
        assertSame(configs, HomeLayoutEditor.move(configs, "zzz", "a"))
        assertSame(configs, HomeLayoutEditor.move(configs, "b", "b"))
    }

    @Test
    fun `field edits touch only the named shelf`() {
        val hidden = HomeLayoutEditor.setEnabled(configs, "b", false)
        assertEquals(listOf(true, false, true, true), hidden.map { it.enabled })
        val styled = HomeLayoutEditor.setStyle(configs, "c", HomeSectionStyle.LARGE_GRID)
        assertEquals(HomeSectionStyle.LARGE_GRID, styled[2].style)
        assertEquals(null, HomeLayoutEditor.setStyle(styled, "c", null)[2].style)
        assertEquals(6, HomeLayoutEditor.setCount(configs, "a", 6)[0].count)
    }
}
