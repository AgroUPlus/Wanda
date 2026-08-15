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
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A resolved playable audio format paired with the client identity that produced it — the
 * stream URL has to be fetched with a matching User-Agent, or googlevideo refuses it. */
internal data class PlayerResponse(
    val format: JsonObject,
    val variant: InnerTubeVariant,
    /** When present, must be appended to the stream URL as a `pot` query param. */
    val streamingPoToken: String? = null
)

/**
 * Minimal InnerTube (YouTube Music private API) client. Search, browse and library calls carry
 * the user's own cookie and a SAPISID hash, exactly as the web player does. Playback (`/player`)
 * calls are anonymous — see [player] — and nothing is sent anywhere but music.youtube.com.
 */
@Singleton
class InnerTubeClient @Inject constructor(
    private val accountManager: GoogleAccountManager,
    private val client: HttpClient
) {

    /** Pairs the PO Token request with the player request when no visitor ID is available yet. */
    private val fallbackSessionId = UUID.randomUUID().toString()

    suspend fun search(query: String): Result<JsonObject> = post(
        "search",
        buildJsonObject {
            put("context", webContext())
            put("query", query)
            put("params", SONGS_FILTER)
        }
    )

    /**
     * Two playback identities, in this order, and no others.
     *
     * `ANDROID_VR` needs no PO Token, no cookie and no signature descrambling — its formats carry
     * a plain `url` — so it is the cheap happy path. It does return LOGIN_REQUIRED with no formats
     * on some tracks, which is what the signed-in `WEB_REMIX` identity plus a real PO Token is for.
     * `WEB_EMBEDDED` used to sit at the head of this chain and is gone: it answers `/player` with
     * `playabilityStatus: ERROR` and zero formats on both hosts (verified 2026-08-14), so it only
     * ever cost a round-trip. Anything added back here needs the same check first.
     */
    internal suspend fun player(videoId: String): Result<PlayerResponse> {
        val vr = playerAs(videoId, InnerTubeVariant.ANDROID_VR)
        vr.getOrNull()?.let { return Result.success(it) }

        val web = playerAs(videoId, InnerTubeVariant.WEB_REMIX)
        web.getOrNull()?.let { return Result.success(it) }

        // Reporting only the last variant's error made every failure read as a WEB_REMIX problem,
        // hiding which identity YouTube actually refused and why.
        return Result.failure(
            IOException(
                "ANDROID_VR: ${vr.exceptionOrNull()?.message ?: "failed"} | " +
                    "WEB_REMIX: ${web.exceptionOrNull()?.message ?: "failed"}"
            )
        )
    }

    private suspend fun playerAs(videoId: String, variant: InnerTubeVariant): Result<PlayerResponse> {
        val isWeb = variant == InnerTubeVariant.WEB_REMIX
        // The PO Token is bound to this exact ID at mint time; YouTube checks the two match, so
        // whichever ID is used here has to also travel in the request (context.client.visitorData
        // and X-Goog-Visitor-Id below) — not just be self-consistent locally.
        val sessionId = accountManager.visitorData.ifBlank { fallbackSessionId }
        val poToken = if (isWeb) {
            runCatching { PoTokenGenerator().getWebClientPoToken(videoId, sessionId) }.getOrNull()
        } else {
            null
        }
        // Omitting this makes the request look like a stale cached page to YouTube, which answers
        // with a generic "the page needs to be reloaded" UNPLAYABLE — indistinguishable from a
        // real refusal unless you already know what that message means.
        val signatureTimestamp = if (isWeb) {
            runCatching { CipherDeobfuscator.signatureTimestamp() }.getOrNull()
        } else {
            null
        }

        return post(
            "player",
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", variant.contextClientName)
                        put("clientVersion", variant.clientVersion)
                        put("hl", deviceLanguage())
                        put("gl", deviceCountry())
                        if (isWeb) {
                            put("visitorData", sessionId)
                        } else {
                            put("androidSdkVersion", ANDROID_VR_SDK_VERSION)
                            put("osName", "Android")
                            put("osVersion", ANDROID_VR_OS_VERSION)
                            put("deviceMake", "Oculus")
                            put("deviceModel", "Quest 3")
                        }
                    }
                }
                if (isWeb) {
                    putJsonObject("playbackContext") {
                        putJsonObject("contentPlaybackContext") {
                            put("html5Preference", "HTML5_PREF_WANTS")
                            put("referer", "$ORIGIN/")
                            signatureTimestamp?.let { put("signatureTimestamp", it) }
                        }
                    }
                    poToken?.playerRequestPoToken?.let { token ->
                        putJsonObject("serviceIntegrityDimensions") { put("poToken", token) }
                    }
                }
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            },
            client = variant,
            visitorIdOverride = sessionId.takeIf { isWeb }
        ).mapCatching { body ->
            // A response can be HTTP 200 while still refusing to play (playabilityStatus != OK), so
            // success has to be judged by that field rather than by the HTTP status — otherwise a
            // refusal never reaches the fallback above.
            val format = body.bestAudioFormat()
                ?: throw IOException("YouTube Music returned no playable audio for this track")
            PlayerResponse(format, variant, poToken?.streamingDataPoToken)
        }
    }

    /**
     * The account's own home feed. `FEmusic_home` is the browse id behind music.youtube.com's
     * front page, so this returns exactly the shelves YouTube Music would show that user.
     */
    suspend fun home(): Result<JsonObject> = browse(HOME_BROWSE_ID)

    suspend fun browse(browseId: String): Result<JsonObject> = post(
        "browse",
        buildJsonObject {
            put("context", webContext())
            put("browseId", browseId)
        }
    )

    /**
     * The radio queue for a track.
     *
     * `playlistId` is what makes this a radio. Without it `next` answers with the watch queue —
     * literally the one track you asked about — which is why "start radio" produced a station of
     * the same song over and over. `RDAMVM<videoId>` is YouTube Music's own id for "radio based on
     * this video", and returns ~50 tracks mixing the artist with stylistic neighbours.
     */
    suspend fun next(videoId: String): Result<JsonObject> = post(
        "next",
        buildJsonObject {
            put("context", webContext())
            put("enablePersistentPlaylistPanel", true)
            put("isAudioOnly", true)
            put("videoId", videoId)
            put("playlistId", "$RADIO_PREFIX$videoId")
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
        client: InnerTubeVariant = InnerTubeVariant.WEB_REMIX,
        visitorIdOverride: String? = null
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val response = this@InnerTubeClient.client.post("${client.apiBaseUrl}/$endpoint") {
                applyHeaders(client, visitorIdOverride)
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
     * Only `WEB_REMIX` (search/browse/library) carries cookie auth: it's the signed-in browser
     * identity, authenticated with a `SAPISIDHASH` computed over [ORIGIN] — Google re-derives the
     * hash from the `Origin`/`X-Origin` header, so the two always travel together. The playback
     * client (`ANDROID_VR`) is deliberately anonymous: it neither accepts nor needs cookie auth
     * for `/player`, and sending it would just make the request read as a mismatched, suspicious
     * identity.
     */
    private fun HttpRequestBuilder.applyHeaders(
        variant: InnerTubeVariant,
        visitorIdOverride: String? = null
    ) {
        contentType(ContentType.Application.Json)
        header("User-Agent", variant.userAgent)
        header("X-YouTube-Client-Name", variant.clientId)
        header("X-YouTube-Client-Version", variant.clientVersion)

        if (variant != InnerTubeVariant.WEB_REMIX) return

        header("Origin", ORIGIN)
        header("X-Origin", ORIGIN)
        header("Referer", "$ORIGIN/")
        (visitorIdOverride ?: accountManager.visitorData).takeIf { it.isNotBlank() }
            ?.let { header("X-Goog-Visitor-Id", it) }

        val cookie = accountManager.authCookie
        if (cookie.isBlank()) return
        header("Cookie", cookie)
        SAPISID_REGEX.find(cookie)?.groupValues?.get(1)?.let { header("Authorization", sapisidHash(it)) }
    }

    private fun webContext(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", InnerTubeVariant.WEB_REMIX.contextClientName)
            put("clientVersion", InnerTubeVariant.WEB_REMIX.clientVersion)
            put("hl", deviceLanguage())
            put("gl", deviceCountry())
        }
    }

    /**
     * A client that declares a region other than the one YouTube sees from the request's IP gets
     * treated as suspicious — tracks that are perfectly playable come back LOGIN_REQUIRED or
     * UNPLAYABLE. Always describing the device's actual locale (rather than a hardcoded "en"/"US")
     * keeps the declared identity consistent with where the request is actually coming from.
     */
    private fun deviceLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

    private fun deviceCountry(): String = Locale.getDefault().country.ifBlank { "US" }

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="

        /** The browse id behind music.youtube.com's front page. */
        const val HOME_BROWSE_ID = "FEmusic_home"

        /** YouTube Music's id for "radio seeded by this video". */
        const val RADIO_PREFIX = "RDAMVM"
        const val ANDROID_VR_SDK_VERSION = 32
        const val ANDROID_VR_OS_VERSION = "12L"

        val SAPISID_REGEX = Regex("(?:__Secure-3PAPISID|SAPISID)=([^;]+)")

        fun sapisidHash(sapisid: String): String {
            val time = System.currentTimeMillis() / 1000
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$time $sapisid $ORIGIN".toByteArray(Charsets.UTF_8))
            return "SAPISIDHASH ${time}_${digest.joinToString("") { "%02x".format(it) }}"
        }
    }
}
