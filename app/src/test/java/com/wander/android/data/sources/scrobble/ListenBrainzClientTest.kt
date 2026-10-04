package com.wander.android.data.sources.scrobble

import com.wander.android.core.database.dao.PendingScrobble
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is sent to ListenBrainz, and what each answer it gives is taken to mean. */
class ListenBrainzClientTest {

    private var sent: Request? = null

    private fun client(code: Int, body: String = "{}") = ListenBrainzClient(
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            sent = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
    )

    private val plays = listOf(
        PendingScrobble(1, 1_000_000L, "Jóga", "Björk", "Homogenic", null, 305_000L),
        PendingScrobble(2, 2_000_000L, "Hunter", "Björk", null, null, 0L)
    )

    @Test
    fun aBatchIsAnImportWithTheTokenInTheHeaderNotTheUrl() = runTest {
        assertEquals(ScrobbleOutcome.Done, client(200).submit("secret-token", plays))
        val request = sent!!
        assertEquals("Token secret-token", request.header("Authorization"))
        assertTrue("token leaked into the URL", "secret-token" !in request.url.toString())

        val body = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
        assertEquals("import", body["listen_type"]!!.jsonPrimitive.content)
        val first = body["payload"]!!.jsonArray[0].jsonObject
        val metadata = first["track_metadata"]!!.jsonObject
        assertEquals("Björk", metadata["artist_name"]!!.jsonPrimitive.content)
        assertEquals("Homogenic", metadata["release_name"]!!.jsonPrimitive.content)
        assertEquals("305000", metadata["additional_info"]!!.jsonObject["duration_ms"]!!.jsonPrimitive.content)
        assertEquals(plays[0].startedAtSeconds().toString(), first["listened_at"]!!.jsonPrimitive.content)
        assertTrue("no release for a play without one", "release_name" !in body["payload"]!!.jsonArray[1].jsonObject["track_metadata"]!!.jsonObject)
    }

    @Test
    fun answersMapToWhatTheWorkerDoesNext() = runTest {
        assertTrue(client(401).submit("t", plays) is ScrobbleOutcome.Unauthorized)
        assertTrue(client(503).submit("t", plays) is ScrobbleOutcome.Retry)
        assertTrue(client(429).submit("t", plays) is ScrobbleOutcome.Retry)
        // A listen it will never accept is not sent forever.
        assertEquals(ScrobbleOutcome.Done, client(400).submit("t", plays))
    }

    @Test
    fun aTokenIsKeptOnlyWhenListenBrainzSaysItIsValid() = runTest {
        assertEquals("bjork", client(200, """{"valid":true,"user_name":"bjork"}""").validate("t").getOrThrow())
        assertTrue(client(200, """{"valid":false}""").validate("t").isFailure)
    }
}
