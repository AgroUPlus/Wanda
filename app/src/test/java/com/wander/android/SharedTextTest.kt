package com.wander.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {

    @Test
    fun aBareLinkIsItself() {
        assertEquals(
            "https://music.youtube.com/watch?v=abc123",
            SharedText.firstLink("https://music.youtube.com/watch?v=abc123")
        )
    }

    @Test
    fun theLinkIsFoundInsideASentence() {
        assertEquals(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=x",
            SharedText.firstLink("Listen to Today's Top Hits on Spotify: https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=x")
        )
    }

    @Test
    fun punctuationEndingTheSentenceIsNotPartOfTheLink() {
        assertEquals("https://youtu.be/abc123", SharedText.firstLink("Have you heard this (https://youtu.be/abc123)."))
    }

    @Test
    fun theFirstOfSeveralLinksWins() {
        assertEquals(
            "https://www.deezer.com/playlist/123",
            SharedText.firstLink("https://www.deezer.com/playlist/123 and https://youtu.be/abc")
        )
    }

    @Test
    fun wandaLinksAreFound() {
        assertEquals("wanda://listen?id=42", SharedText.firstLink("open wanda://listen?id=42"))
    }

    @Test
    fun textWithoutALinkHasNone() {
        assertNull(SharedText.firstLink("just some words"))
        assertNull(SharedText.firstLink(""))
        assertNull(SharedText.firstLink(null))
        assertNull(SharedText.firstLink("https://"))
    }
}
