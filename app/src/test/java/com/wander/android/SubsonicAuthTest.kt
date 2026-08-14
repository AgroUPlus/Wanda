package com.wander.android

import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.sources.navidrome.SubsonicApiClient
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {

    @Test
    fun testSubsonicClientConfiguration() {
        val client = SubsonicApiClient(HttpClientFactory.ktorClient)
        client.configure("https://demo.navidrome.org", "testuser", "secretpass")
        assertTrue(client.isConfigured)

        val streamUrl = client.buildStreamUrl("track-123")
        assertTrue(streamUrl.contains("rest/stream.view"))
        assertTrue(streamUrl.contains("id=track-123"))
        assertTrue(streamUrl.contains("u=testuser"))
        assertTrue(streamUrl.contains("t="))
        assertTrue(streamUrl.contains("s="))
    }
}
