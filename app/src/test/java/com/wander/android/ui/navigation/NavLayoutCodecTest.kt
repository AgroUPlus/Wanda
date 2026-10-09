package com.wander.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavLayoutCodecTest {

    private fun decode(raw: String?) = NavLayoutCodec.decode(raw, DockItem.entries, DockItem.Default)

    @Test
    fun `nothing saved means the default`() {
        assertEquals(DockItem.Default, decode(null))
    }

    @Test
    fun `a saved list keeps its order`() {
        assertEquals(listOf(DockItem.STATS, DockItem.LIBRARY), decode("STATS,LIBRARY"))
    }

    @Test
    fun `an empty saved list is a choice of nothing`() {
        assertEquals(emptyList<DockItem>(), decode(""))
    }

    @Test
    fun `unknown names and repeats are dropped`() {
        assertEquals(listOf(DockItem.FRIENDS), decode("GONE,FRIENDS,FRIENDS"))
    }

    @Test
    fun `encode round trips`() {
        val items = listOf(DockItem.SETTINGS, DockItem.HISTORY)
        assertEquals(items, decode(NavLayoutCodec.encode(items)))
    }
}
