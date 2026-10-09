package com.wander.android.ui.screens.home

import com.wander.android.ui.screens.home.layout.ShelfSuggestions
import com.wander.android.ui.screens.home.layout.ShelfUsage
import com.wander.android.ui.screens.home.layout.ShelfUsageCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfSuggestionsTest {

    private val day = 24L * 60 * 60 * 1000
    private val shelves = listOf("a", "b", "c", "d", "e")

    private fun usage(since: Long, vararg plays: Pair<String, Int>) = ShelfUsage(since, mapOf(*plays))

    @Test
    fun `too little time gives no advice`() {
        assertEquals(emptyList<String>(), ShelfSuggestions.rarelyUsed(shelves, usage(0, "a" to 9), now = 3 * day))
        assertEquals(emptyList<String>(), ShelfSuggestions.rarelyUsed(shelves, usage(1, "a" to 9), now = 4 * day))
    }

    @Test
    fun `too few plays gives no advice`() {
        assertEquals(emptyList<String>(), ShelfSuggestions.rarelyUsed(shelves, usage(1, "a" to 2), now = 20 * day))
    }

    @Test
    fun `shelves never played are suggested in order, at most three`() {
        val result = ShelfSuggestions.rarelyUsed(shelves, usage(1, "a" to 6), now = 20 * day)
        assertEquals(listOf("b", "c", "d"), result)
    }

    @Test
    fun `recording counts and starts the clock once`() {
        val first = ShelfUsage().recorded("a", now = 100)
        val second = first.recorded("a", now = 900).recorded("b", now = 950)
        assertEquals(100, second.since)
        assertEquals(2, second.playsFrom("a"))
        assertEquals(1, second.playsFrom("b"))
        assertEquals(0, second.playsFrom("zzz"))
    }

    @Test
    fun `usage round trips and survives garbage`() {
        val usage = ShelfUsage(5, mapOf("a" to 3))
        assertEquals(usage, ShelfUsageCodec.decode(ShelfUsageCodec.encode(usage)))
        assertEquals(ShelfUsage(), ShelfUsageCodec.decode("{oops"))
        assertEquals(ShelfUsage(), ShelfUsageCodec.decode(null))
    }
}
