package com.wander.android.data.sources.navidrome

import com.wander.android.core.network.HttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubsonicCredentialsTest {

    private val credentials = SubsonicCredentials().apply {
        configure("https://music.example.com/navidrome/", "alice", "secret")
    }

    @Test
    fun coverArtUrlCarriesNoCredentials() {
        val client = SubsonicApiClient(HttpClientFactory.ktorClient, credentials)
        val url = client.buildCoverArtUrl("al-1")!!.toHttpUrl()

        assertEquals("/navidrome/rest/getCoverArt.view", url.encodedPath)
        assertEquals("al-1", url.queryParameter("id"))
        assertFalse(url.queryParameterNames.any { it in setOf("u", "t", "s", "p") })
    }

    @Test
    fun signsUnsignedRequestsToTheConfiguredServer() {
        val signed = credentials.sign("https://music.example.com/navidrome/rest/getCoverArt.view?id=al-1".toHttpUrl())

        assertEquals("alice", signed.queryParameter("u"))
        assertNotNull(signed.queryParameter("t"))
        assertNotNull(signed.queryParameter("s"))
        assertEquals("al-1", signed.queryParameter("id"))
    }

    @Test
    fun leavesOtherHostsAndPathsAlone() {
        listOf(
            "https://evil.example.org/navidrome/rest/getCoverArt.view?id=al-1",
            "http://music.example.com/navidrome/rest/getCoverArt.view?id=al-1",
            "https://music.example.com:8443/navidrome/rest/getCoverArt.view?id=al-1",
            "https://music.example.com/other/rest/getCoverArt.view?id=al-1"
        ).forEach { raw ->
            assertNull(raw, credentials.sign(raw.toHttpUrl()).queryParameter("t"))
        }
    }

    @Test
    fun keepsAnExistingSignature() {
        val url = "https://music.example.com/navidrome/rest/getCoverArt.view?id=al-1&u=alice&t=abc&s=def".toHttpUrl()
        assertEquals(url, credentials.sign(url))
    }

    @Test
    fun signsNothingOnceSignedOut() {
        credentials.configure("", "", "")
        val url = "https://music.example.com/navidrome/rest/getCoverArt.view?id=al-1".toHttpUrl()
        assertEquals(url, credentials.sign(url))
    }
}
