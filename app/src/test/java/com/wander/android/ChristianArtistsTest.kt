package com.wander.android

import com.wander.android.data.christian.ChristianArtists
import com.wander.android.data.christian.ChristianLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChristianArtistsTest {

    @Test
    fun `credit matches ignoring case and accents`() {
        assertTrue(ChristianArtists.credits("julia vitoria", "Julia Vitória"))
        assertTrue(ChristianArtists.credits("HILLSONG WORSHIP", "Hillsong Worship"))
    }

    @Test
    fun `credit with several artists matches each`() {
        assertTrue(ChristianArtists.credits("Fernandinho, Anderson Freire", "Anderson Freire"))
        assertTrue(ChristianArtists.credits("Fernandinho & Aline Barros", "Aline Barros"))
    }

    @Test
    fun `whole words only`() {
        assertFalse(ChristianArtists.credits("Moradas Vivas", "Morada"))
        assertFalse(ChristianArtists.credits("Someone Else", "Morada"))
    }

    @Test
    fun `punctuation in a name does not break the match`() {
        assertTrue(ChristianArtists.credits("for KING & COUNTRY", "for KING & COUNTRY"))
    }

    @Test
    fun `only languages with artists are offered`() {
        assertEquals(listOf(ChristianLanguage.PORTUGUESE, ChristianLanguage.ENGLISH), ChristianArtists.languages)
    }
}
