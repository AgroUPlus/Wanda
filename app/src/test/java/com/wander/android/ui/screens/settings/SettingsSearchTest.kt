package com.wander.android.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private val trueBlack =
        SettingsSearchEntry(label = 1, category = SettingsCategory.APPEARANCE, focusKey = "look_colours")
    private val offline = SettingsSearchEntry(label = 2, category = SettingsCategory.PLAYBACK, focusKey = "playback")
    private val index = listOf(trueBlack, offline)

    private val text = mapOf(
        1 to "True black",
        2 to "Offline mode",
        SettingsCategory.APPEARANCE.label to "Look & feel",
        SettingsCategory.PLAYBACK.label to "Playback & storage"
    )

    private fun search(query: String) = searchSettings(query, { text.getValue(it) }, index)

    @Test
    fun `matches a label regardless of case`() {
        assertEquals(listOf(trueBlack), search("BLACK"))
    }

    @Test
    fun `every word must match, in the label or the page name`() {
        assertEquals(listOf(offline), search("offline storage"))
        assertTrue(search("offline black").isEmpty())
    }

    @Test
    fun `accents are ignored`() {
        assertEquals(listOf(offline), search("öffline"))
    }

    @Test
    fun `a blank query finds nothing`() {
        assertTrue(search("   ").isEmpty())
    }
}
