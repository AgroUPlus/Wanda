package com.wander.android

import com.wander.android.data.model.RecommendedShelf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedShelfTest {

    private fun shelf(title: String, id: String = "ytm_x") = RecommendedShelf(id, title)

    @Test
    fun `kinds of music are kept`() {
        assertTrue(shelf("Mixed for you").isGeneric)
        assertTrue(shelf("Quick picks").isGeneric)
        assertTrue(shelf("Charts: Top 100").isGeneric)
    }

    @Test
    fun `shelves about one song or artist are dropped`() {
        assertFalse(shelf("Troye Sivan - She's the Best").isGeneric)
        assertFalse(shelf("Daft Punk \u2013 Discovery").isGeneric)
    }

    @Test
    fun `personal because-you shelves are dropped so Home makes only its own`() {
        assertFalse(shelf("Because you listened to Dua Lipa").isGeneric)
        assertFalse(shelf("Similar to Coldplay").isGeneric)
        assertFalse(shelf("More like Radiohead").isGeneric)
    }

    @Test
    fun `listen again and music videos are dropped`() {
        assertFalse(shelf("Listen again", id = "ytm_listen_again").isGeneric)
        assertFalse(shelf("New music videos").isGeneric)
    }

    @Test
    fun `a hyphen inside a word is not a separator`() {
        assertTrue(shelf("K-pop hits").isGeneric)
    }
}
