package com.wander.android.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class DropNotificationTextTest {

    private val quote: (String) -> String = { "«$it»" }

    @Test
    fun `a track reads title then artist`() {
        assertEquals("Hey Jude — The Beatles", DropNotificationText.trackLine("Hey Jude", "The Beatles"))
    }

    @Test
    fun `no artist leaves the title alone, without a dangling dash`() {
        assertEquals("Hey Jude", DropNotificationText.trackLine(" Hey Jude ", "  "))
    }

    @Test
    fun `a note goes under the track in the language's quotes`() {
        assertEquals(
            "Hey Jude — The Beatles\n«for you»",
            DropNotificationText.message("Hey Jude", "The Beatles", " for you ", quote)
        )
    }

    @Test
    fun `no note, or a blank one, is just the track`() {
        assertEquals("Song — Band", DropNotificationText.message("Song", "Band", null, quote))
        assertEquals("Song — Band", DropNotificationText.message("Song", "Band", "   ", quote))
    }
}
