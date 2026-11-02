package com.wander.android.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecureSearchHistoryTest {

    @Test
    fun `entries survive a reload in order`() {
        val prefs = FakeSharedPreferences()
        SecureSearchHistory(prefs).write(listOf("radiohead", "bjork", "massive attack"))

        assertEquals(listOf("radiohead", "bjork", "massive attack"), SecureSearchHistory(prefs).entries.value)
    }

    @Test
    fun `clearing removes the key instead of storing an empty string`() {
        val prefs = FakeSharedPreferences()
        val history = SecureSearchHistory(prefs)
        history.write(listOf("radiohead"))

        history.write(emptyList())

        assertFalse(prefs.contains(KEY_SEARCH_HISTORY))
        assertEquals(emptyList<String>(), history.entries.value)
    }
}
