package com.wander.android.data.sources.ytmusic

import com.wander.android.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal InnerTube (YouTube Music private API) client. Requests carry the user's own cookie and
 * a SAPISID hash, exactly as the web player does; nothing is sent anywhere else.
 */
@Singleton
class InnerTubeClient @Inject constructor(
    private val accountManager: GoogleAccountManager,
    private val client: HttpClient
) {

    suspend fun search(query: String): Result<JsonObject> = post(
        "search",
        buildJsonObject {
            put("context", webContext())
            put("query", query)
            put("params", SONGS_FILTER)
        }
    )

    /**
     * The Android Music client returns direct, un-throttled Opus URLs, which is why the player
     * request uses a different context from everything else.
     *
     * The identity must be coherent end to end: body context, request headers and the later
     * googlevideo media fetch all have to look like the same client. Declaring ANDROID_MUSIC in
     * the body while sending WEB_REMIX headers gets the request refused.
     */
    suspend fun player(videoId: String): Result<JsonObject> = post(
        "player",
        buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_MUSIC")
                    put("clientVersion", ANDROID_MUSIC_VERSION)
                    put("androidSdkVersion", ANDROID_SDK_VERSION)
                    put("osName", "Android")
                    put("osVersion", ANDROID_OS_VERSION)
                    put("androidPackage", ANDROID_MUSIC_PACKAGE)
                    put("platform", "MOBILE")
                    put("hl", "en")
                    put("gl", "US")
                    // Carried in the body rather than as X-Goog-Visitor-Id, so this request
                    // describes exactly one session: an anonymous Android client.
                    accountManager.visitorData.takeIf { it.isNotBlank() }
                        ?.let { put("visitorData", it) }
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        },
        client = InnerTubeVariant.ANDROID_MUSIC
    )

    suspend fun browse(browseId: String): Result<JsonObject> = post(
        "browse",
        buildJsonObject {
            put("context", webContext())
            put("browseId", browseId)
        }
    )

    suspend fun next(videoId: String): Result<JsonObject> = post(
        "next",
        buildJsonObject {
            put("context", webContext())
            put("enablePersistentPlaylistPanel", true)
            put("isAudioOnly", true)
            put("videoId", videoId)
        }
    )

    suspend fun setLiked(videoId: String, liked: Boolean): Result<Unit> = post(
        if (liked) "like/like" else "like/removelike",
        buildJsonObject {
            put("context", webContext())
            putJsonObject("target") { put("videoId", videoId) }
        }
    ).map { }

    private suspend fun post(
        endpoint: String,
        payload: JsonObject,
        client: InnerTubeVariant = InnerTubeVariant.WEB_REMIX
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val response = this@InnerTubeClient.client.post("$BASE_URL/$endpoint") {
                applyHeaders(client)
                setBody(payload.toString())
            }
            // A rejected request still returns parseable JSON, just without the fields we want.
            // Without this check an auth failure or a bot challenge is indistinguishable from a
            // video that genuinely has no audio.
            if (!response.status.isSuccess()) {
                throw IOException(
                    "YouTube Music refused the $endpoint request (HTTP ${response.status.value})"
                )
            }
            HttpClientFactory.jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
        }
    }

    /**
     * Each identity has to be internally coherent, and the two identities authenticate differently.
     *
     * `WEB_REMIX` is the signed-in browser: cookie plus a `SAPISIDHASH` computed over [ORIGIN],
     * which Google re-derives from the `Origin`/`X-Origin` header — so the hash and the header
     * must always travel together.
     *
     * `ANDROID_MUSIC` is an OAuth client and does **not** accept cookie auth. Sending it the web
     * cookie and a hash it has no matching origin for made the whole request read as an invalid
     * session, and InnerTube answered `playabilityStatus = LOGIN_REQUIRED` — playback failed while
     * search and metadata (both `WEB_REMIX`) kept working. It fetches streams unauthenticated.
     */
    private fun HttpRequestBuilder.applyHeaders(variant: InnerTubeVariant) {
        contentType(ContentType.Application.Json)
        header("User-Agent", variant.userAgent)
        header("X-YouTube-Client-Name", variant.clientId)
        header("X-YouTube-Client-Version", variant.clientVersion)

        if (variant != InnerTubeVariant.WEB_REMIX) return

        header("Origin", ORIGIN)
        header("X-Origin", ORIGIN)
        header("Referer", "$ORIGIN/")
        accountManager.visitorData.takeIf { it.isNotBlank() }?.let { header("X-Goog-Visitor-Id", it) }

        val cookie = accountManager.authCookie
        if (cookie.isBlank()) return
        header("Cookie", cookie)
        SAPISID_REGEX.find(cookie)?.groupValues?.get(1)?.let { header("Authorization", sapisidHash(it)) }
    }

    private fun webContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", InnerTubeVariant.WEB_REMIX.clientVersion)
            put("hl", "en")
            put("gl", "US")
        }
    }

    private companion object {
        const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        const val ORIGIN = "https://music.youtube.com"
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        const val ANDROID_SDK_VERSION = 34
        const val ANDROID_OS_VERSION = "14"
        const val ANDROID_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

        val ANDROID_MUSIC_VERSION = InnerTubeVariant.ANDROID_MUSIC.clientVersion

        val SAPISID_REGEX = Regex("(?:__Secure-3PAPISID|SAPISID)=([^;]+)")

        fun sapisidHash(sapisid: String): String {
            val time = System.currentTimeMillis() / 1000
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$time $sapisid $ORIGIN".toByteArray(Charsets.UTF_8))
            return "SAPISIDHASH ${time}_${digest.joinToString("") { "%02x".format(it) }}"
        }
    }
}
