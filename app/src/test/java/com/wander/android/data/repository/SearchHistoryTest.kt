package com.wander.android.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun `a new search goes to the front`() {
        assertEquals(listOf("bjork", "radiohead"), listOf("radiohead").withQuery("bjork"))
    }

    @Test
    fun `searching again moves the entry up instead of repeating it`() {
        assertEquals(
            listOf("Radiohead", "bjork"),
            listOf("bjork", "radiohead").withQuery("Radiohead")
        )
    }

    @Test
    fun `whitespace is folded so an entry stays on one line`() {
        assertEquals(listOf("massive attack"), emptyList<String>().withQuery("  massive\n  attack "))
    }

    @Test
    fun `a blank search changes nothing`() {
        assertEquals(listOf("bjork"), listOf("bjork").withQuery("   "))
    }

    @Test
    fun `the oldest entry falls off past the limit`() {
        val full = (1..MAX_SEARCH_HISTORY).map { "query $it" }

        val updated = full.withQuery("newest")

        assertEquals(MAX_SEARCH_HISTORY, updated.size)
        assertEquals("newest", updated.first())
        assertEquals("query ${MAX_SEARCH_HISTORY - 1}", updated.last())
    }
}
