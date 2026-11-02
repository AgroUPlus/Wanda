package com.wander.android.data.sources.deezer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deezer's private web gateway (`gw-light.php`), which hands out the tokens a stream needs.
 *
 * Two things here are easy to get wrong, and both used to be. The gateway answers HTTP 200 for
 * its own failures and puts the reason in an `error` field, so a refused call has to be read as
 * one rather than as an empty answer. And every method except `deezer.getUserData` needs that
 * call's `checkForm` as its `api_token`, sent within the same gateway session (`sid`) — an empty
 * token is refused as `VALID_TOKEN_REQUIRED`.
 */
@Singleton
class DeezerGateway @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val accountManager: DeezerAccountManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val sessionMutex = Mutex()

    /**
     * The gateway session, with the `arl` it was opened for.
     *
     * Keyed on the `arl` rather than held bare: sign-out, a different account signing in, and
     * "forget everything" all change the stored `arl` without passing through here, and a bare
     * token outlived every one of them — the next account streamed on the previous one's license.
     */
    @Volatile
    private var cached: Session? = null

    /** Not a data class on purpose: a generated `toString` would print the arl and both tokens. */
    class Session(
        val arl: String,
        val apiToken: String,
        val licenseToken: String,
        val sid: String?
    ) {
        val cookie: String get() = "arl=$arl" + sid?.let { "; sid=$it" }.orEmpty()
    }

    /** A refusal the gateway reported in its `error` field. */
    class GatewayException(val codes: Set<String>) :
        IOException("Deezer refused the request (${codes.joinToString()})") {
        val isSessionExpired: Boolean get() = codes.any { it in SESSION_ERRORS }
    }

    suspend fun session(arl: String): Result<Session> = sessionMutex.withLock {
        cached?.takeIf { it.arl == arl }?.let { return Result.success(it) }
        runCatching { openSession(arl) }.onSuccess { cached = it }
    }

    fun invalidate() {
        cached = null
    }

    /** The `TRACK_TOKEN` [DeezerStreamResolver] trades for a media URL. */
    fun trackToken(session: Session, cleanTrackId: String): Result<String> = runCatching {
        val payload = buildJsonObject { put("sng_id", cleanTrackId) }
        val results = call("song.getData", session.apiToken, session.cookie, payload.toString()).first
        results["TRACK_TOKEN"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Deezer returned no track token for this song")
    }

    private fun openSession(arl: String): Session {
        val (results, headers) = call("deezer.getUserData", apiToken = "", cookie = "arl=$arl", body = "{}")
        val options = results["OPTIONS"]?.jsonObject
        val licenseToken = options?.get("license_token")?.jsonPrimitive?.contentOrNull
            ?: throw IOException("No license token in Deezer user data")
        val apiToken = results["checkForm"]?.jsonPrimitive?.contentOrNull
            ?: throw IOException("No API token in Deezer user data")

        val isLossless = options["web_lossless"]?.jsonPrimitive?.booleanOrNull == true
        val isHq = options["web_hq"]?.jsonPrimitive?.booleanOrNull == true
        accountManager.updateTier(DeezerAccountTier.fromFlags(isLossless, isHq))
        results["USER"]?.jsonObject?.get("BLOG_NAME")?.jsonPrimitive?.contentOrNull?.let {
            accountManager.updateAccountName(it)
        }
        return Session(arl, apiToken, licenseToken, sidOf(headers))
    }

    /** One gateway call: its `results`, and the response's headers for the session cookie. */
    private fun call(method: String, apiToken: String, cookie: String, body: String): Pair<JsonObject, Headers> {
        val url = GW_LIGHT_URL.toHttpUrl().newBuilder()
            .addQueryParameter("method", method)
            .addQueryParameter("api_version", "1.0")
            .addQueryParameter("api_token", apiToken)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Deezer gateway $method failed: ${response.code}")
            val text = response.body?.string() ?: throw IOException("Empty Deezer gateway response")
            val root = json.parseToJsonElement(text).jsonObject
            val refused = errorCodes(root["error"])
            if (refused.isNotEmpty()) throw GatewayException(refused)
            val results = root["results"] as? JsonObject
                ?: throw IOException("Deezer gateway $method returned no results")
            results to response.headers
        }
    }

    private fun sidOf(headers: Headers): String? =
        headers.values("Set-Cookie")
            .map { it.substringBefore(';').trim() }
            .firstOrNull { it.startsWith("sid=") }
            ?.removePrefix("sid=")
            ?.takeIf { it.isNotBlank() }

    internal companion object {
        const val GW_LIGHT_URL = "https://www.deezer.com/ajax/gw-light.php"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val SESSION_ERRORS = setOf("VALID_TOKEN_REQUIRED", "NEED_USER_AUTH_REQUIRED")

        /**
         * The codes in a gateway `error` field. A successful call sends an empty array there, a
         * refused one an object keyed by code — `{"VALID_TOKEN_REQUIRED": "Invalid CSRF token"}`.
         */
        fun errorCodes(error: JsonElement?): Set<String> = when (error) {
            is JsonObject -> error.keys
            is JsonArray -> error.mapNotNull { entry ->
                when (entry) {
                    is JsonObject -> entry.keys.firstOrNull()
                    is JsonPrimitive -> entry.contentOrNull
                    else -> null
                }
            }.toSet()
            else -> emptySet()
        }
    }
}
