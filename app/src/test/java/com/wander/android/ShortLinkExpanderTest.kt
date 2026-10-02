package com.wander.android

import com.wander.android.data.importer.ShortLinkExpander
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortLinkExpanderTest {

    @Test
    fun recognisesTheShortLinksAPhoneShareSheetProduces() {
        assertTrue(ShortLinkExpander.isShortLink("https://spotify.link/AbCdEf123"))
        assertTrue(ShortLinkExpander.isShortLink("https://link.deezer.com/s/abc123"))
        assertTrue(ShortLinkExpander.isShortLink("https://deezer.page.link/xyz"))
        assertTrue(ShortLinkExpander.isShortLink("Have a listen https://spotify.link/AbCdEf123"))
    }

    @Test
    fun neverTreatsAnythingElseAsOne() {
        assertFalse(ShortLinkExpander.isShortLink("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertFalse(ShortLinkExpander.isShortLink("https://evil.example/?u=https://spotify.link/x"))
        assertFalse(ShortLinkExpander.isShortLink("https://spotify.link.evil.example/x"))
        assertFalse(ShortLinkExpander.isShortLink("Daft Punk - One More Time"))
    }

    @Test
    fun leavesAFullLinkAloneWithoutTouchingTheNetwork() = runBlocking {
        val link = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"

        assertEquals(link, ShortLinkExpander(HttpClient()).expand(link).getOrThrow())
    }
}
