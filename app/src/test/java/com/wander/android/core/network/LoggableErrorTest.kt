package com.wander.android.core.network

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoggableErrorTest {

    @Test
    fun dropsTheUrlAndKeepsTheStatusFromAKtorStyleMessage() {
        val error = IOException(
            "Client request(GET https://agro.example.org/api/popular?limit=60&token=s3cret) " +
                "invalid: 401 Unauthorized"
        )
        val logged = error.loggableMessage()
        assertEquals("IOException: Client request(GET <url> invalid: 401 Unauthorized", logged)
        assertFalse(logged.contains("s3cret"))
        assertFalse(logged.contains("agro.example.org"))
    }

    @Test
    fun redactsEveryUrlAndAnyScheme() {
        assertEquals(
            "timeout [url=<url> then <url>",
            redactUrls("timeout [url=http://192.168.1.4:4533/rest/ping?u=a&t=b, then wss://x.y/z")
        )
    }

    @Test
    fun leavesTextWithoutUrlsAlone() {
        assertEquals("closed by peer", redactUrls("closed by peer"))
    }

    @Test
    fun namesTheTypeWhenThereIsNoMessage() {
        assertEquals("IOException", IOException().loggableMessage())
    }
}
