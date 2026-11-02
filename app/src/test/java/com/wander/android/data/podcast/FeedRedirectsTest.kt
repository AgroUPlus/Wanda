package com.wander.android.data.podcast

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedRedirectsTest {

    private fun response(url: String, code: Int, prior: Response? = null): Response =
        Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .priorResponse(prior)
            .build()

    @Test
    fun `no redirect is no move`() {
        assertNull(permanentRedirectTarget(response("https://a.example/feed", 200)))
    }

    @Test
    fun `a permanent redirect moves the feed to where it led`() {
        val final = response("https://b.example/feed", 200, prior = response("https://a.example/feed", 301))

        assertEquals("https://b.example/feed", permanentRedirectTarget(final))
    }

    @Test
    fun `a temporary redirect is not remembered`() {
        val final = response("https://b.example/feed", 200, prior = response("https://a.example/feed", 302))

        assertNull(permanentRedirectTarget(final))
    }

    @Test
    fun `the move stops at the last permanent hop before a temporary one`() {
        val first = response("https://a.example/feed", 308)
        val second = response("https://b.example/feed", 307, prior = first)
        val final = response("https://cdn.example/feed", 200, prior = second)

        assertEquals("https://b.example/feed", permanentRedirectTarget(final))
    }
}
