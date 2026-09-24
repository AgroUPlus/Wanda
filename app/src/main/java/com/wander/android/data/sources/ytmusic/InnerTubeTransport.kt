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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level HTTP transport for InnerTube, handling headers, SAPISID authentication digests,
 * locale tags, and JSON payload serialization.
 */
@Singleton
internal class InnerTubeTransport @Inject constructor(
    private val accountManager: GoogleAccountManager,
    private val client: HttpClient
) {
    suspend fun post(
        endpoint: String,
        payload: JsonObject,
        client: InnerTubeVariant = InnerTubeVariant.WEB_REMIX,
        visitorIdOverride: String? = null
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val response = this@InnerTubeTransport.client.post("${client.apiBaseUrl}/$endpoint") {
                applyHeaders(client, visitorIdOverride)
                setBody(payload.toString())
            }
            if (!response.status.isSuccess()) {
                throw IOException(
                    "YouTube Music refused the $endpoint request (HTTP ${response.status.value})"
                )
            }
            HttpClientFactory.jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
        }
    }

    private fun HttpRequestBuilder.applyHeaders(
        variant: InnerTubeVariant,
        visitorIdOverride: String? = null
    ) {
        contentType(ContentType.Application.Json)
        header("User-Agent", variant.userAgent)
        header("X-YouTube-Client-Name", variant.clientId)
        header("X-YouTube-Client-Version", variant.clientVersion)

        if (variant == InnerTubeVariant.VISIONOS) {
            visitorIdOverride?.takeIf { it.isNotBlank() }?.let { header("X-Goog-Visitor-Id", it) }
            return
        }

        if (variant != InnerTubeVariant.WEB_REMIX) return

        header("Origin", YT_MUSIC_ORIGIN)
        header("X-Origin", YT_MUSIC_ORIGIN)
        header("Referer", "$YT_MUSIC_ORIGIN/")
        (visitorIdOverride ?: accountManager.visitorData).takeIf { it.isNotBlank() }
            ?.let { header("X-Goog-Visitor-Id", it) }

        val cookie = accountManager.authCookie
        if (cookie.isBlank()) return
        header("Cookie", cookie)
        SAPISID_REGEX.find(cookie)?.groupValues?.get(1)?.let { header("Authorization", sapisidHash(it)) }
    }

    fun webContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", InnerTubeVariant.WEB_REMIX.contextClientName)
            put("clientVersion", InnerTubeVariant.WEB_REMIX.clientVersion)
            put("hl", deviceLanguage())
            put("gl", deviceCountry())
        }
    }

    fun deviceLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

    fun deviceCountry(): String = Locale.getDefault().country.ifBlank { "US" }

    private companion object {
        val SAPISID_REGEX = Regex("(?:__Secure-3PAPISID|SAPISID)=([^;]+)")

        @Suppress("kotlin:S4790")
        fun sapisidHash(sapisid: String): String {
            val time = System.currentTimeMillis() / 1000
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$time $sapisid $YT_MUSIC_ORIGIN".toByteArray(Charsets.UTF_8))
            return "SAPISIDHASH ${time}_${digest.joinToString("") { "%02x".format(it) }}"
        }
    }
}
