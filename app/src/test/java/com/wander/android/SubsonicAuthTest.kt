package com.wander.android

import com.wander.android.core.network.HttpClientFactory
import com.wander.android.data.sources.navidrome.NavidromeStreamQuality
import com.wander.android.data.sources.navidrome.SubsonicApiClient
import com.wander.android.data.sources.navidrome.SubsonicCredentials
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicAuthTest {

    @Test
    fun testSubsonicClientConfiguration() {
        val client = SubsonicApiClient(HttpClientFactory.ktorClient, SubsonicCredentials())
        client.configure("https://demo.navidrome.org", "testuser", "secretpass")
        assertTrue(client.isConfigured)

        val streamUrl = client.buildStreamUrl("track-123", NavidromeStreamQuality.ORIGINAL)
        assertTrue(streamUrl.contains("rest/stream.view"))
        assertTrue(streamUrl.contains("id=track-123"))
        // Without this the server may transcode on the fly, and a stream generated as it is sent
        // cannot answer a ranged request — which is a track whose seek bar will not move.
        assertTrue(streamUrl.contains("format=raw"))
        assertTrue(streamUrl.contains("u=testuser"))
        assertTrue(streamUrl.contains("t="))
        assertTrue(streamUrl.contains("s="))
    }

    @Test
    fun convertedQualityAsksForThatFormatAndCeiling() {
        val client = SubsonicApiClient(HttpClientFactory.ktorClient, SubsonicCredentials())
        client.configure("https://demo.navidrome.org", "testuser", "secretpass")

        val streamUrl = client.buildStreamUrl("track-123", NavidromeStreamQuality.KBPS_128)
        assertTrue(streamUrl.contains("format=opus"))
        assertTrue(streamUrl.contains("maxBitRate=128"))
        assertTrue(streamUrl.contains("estimateContentLength=true"))
        assertFalse(streamUrl.contains("format=raw"))
    }

    @Test
    fun unknownStoredQualityStreamsTheOriginal() {
        assertTrue(NavidromeStreamQuality.fromKey(null) == NavidromeStreamQuality.ORIGINAL)
        assertTrue(NavidromeStreamQuality.fromKey("KBPS_64") == NavidromeStreamQuality.ORIGINAL)
    }
}
