package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SearchKind
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Null for a livestream, which is served through [hlsManifestUrl] instead of a format list. */
    val format: JsonObject?,
    val variant: InnerTubeVariant,
    /** When present, must be appended to the stream URL as a `pot` query param. */
    val streamingPoToken: String? = null,
    /** Set for a livestream: play this manifest directly, no signature or nonce to resolve. */
    val hlsManifestUrl: String? = null
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

    /**
     * An anonymous visitor session, fetched once and kept for the life of the process.
     *
     * [InnerTubeVariant.VISIONOS] will not mint a livestream manifest without one: with no
     * `visitorData` it answers LOGIN_REQUIRED — "Sign in to confirm you're not a bot" — for every
     * video. A locally generated UUID does not satisfy it either; the value has to be one YouTube
     * issued, which `visitor_id` hands out to anyone who asks, with no account and no cookie.
     *
     * The signed-in session's own visitor id is preferred when there is one, so a user who has
     * connected YouTube Music does not carry two identities around.
     *
     * Cached because it is a per-session identifier, not a per-request one — refetching it for
     * every play would both cost a round-trip and look like a new device each time.
     */
    private val visitorLock = Mutex()
    @Volatile private var cachedVisitorId: String? = null

    private suspend fun visitorSession(): String? {
        accountManager.visitorData.takeIf { it.isNotBlank() }?.let { return it }
        cachedVisitorId?.let { return it }
        return visitorLock.withLock {
            cachedVisitorId ?: fetchVisitorId()?.also { cachedVisitorId = it }
        }
    }

    private suspend fun fetchVisitorId(): String? = post(
        "visitor_id",
        buildJsonObject { put("context", webContext()) },
        client = InnerTubeVariant.WEB_REMIX
    ).getOrNull()?.visitorData()

    suspend fun search(query: String, kind: SearchKind = SearchKind.TRACKS): Result<JsonObject> = post(
        "search",
        buildJsonObject {
            put("context", webContext())
            put("query", query)
            put("params", kind.filterParam())
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
        vr.getOrNull()?.takeIf { it.hlsManifestUrl == null }?.let { return Result.success(it) }

        val web = playerAs(videoId, InnerTubeVariant.WEB_REMIX)
        web.getOrNull()?.takeIf { it.hlsManifestUrl == null }?.let { return Result.success(it) }

        // A livestream goes to VISIONOS even when one of the two above already produced a
        // manifest, which is why they are skipped on `hlsManifestUrl != null` rather than simply
        // being tried first.
        //
        // They do produce one — WEB_REMIX in particular, once the PO Token minter has run — and
        // taking it was the bug behind "Stream expired. Play it again to refresh it." surviving
        // the move off the iOS identity: the manifest and its media playlist both load, and then
        // googlevideo 403s the first media segment. A GVS PO Token is only *recommended* rather
        // than required for HLS on the web music client, and "recommended" is YouTube's word for
        // "sometimes refused". VISIONOS has no token policy at all, so its manifests have nothing
        // to be refused over. See `InnerTubeVariant.VISIONOS`.
        val live = playerAs(videoId, InnerTubeVariant.VISIONOS)
        live.getOrNull()?.let { return Result.success(it) }

        // Only if the token-free identity itself refused. A manifest that might 403 partway is
        // still worth more than no playback at all, and it is the same one the user had before.
        vr.getOrNull()?.let { return Result.success(it) }
        web.getOrNull()?.let { return Result.success(it) }

        // Reporting only the last variant's error made every failure read as a WEB_REMIX problem,
        // hiding which identity YouTube actually refused and why.
        return Result.failure(
            IOException(
                "ANDROID_VR: ${vr.exceptionOrNull()?.message ?: "failed"} | " +
                    "WEB_REMIX: ${web.exceptionOrNull()?.message ?: "failed"} | " +
                    "VISIONOS: ${live.exceptionOrNull()?.message ?: "failed"}"
            )
        )
    }

    private suspend fun playerAs(videoId: String, variant: InnerTubeVariant): Result<PlayerResponse> {
        val isWeb = variant == InnerTubeVariant.WEB_REMIX
        val isLive = variant == InnerTubeVariant.VISIONOS
        // Fetched before the body is built rather than inside it: acquiring a session is a network
        // call, and `buildJsonObject` takes an ordinary lambda.
        val visitorId = if (isLive) visitorSession() else null
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
                        when (variant) {
                            InnerTubeVariant.WEB_REMIX -> put("visitorData", sessionId)
                            InnerTubeVariant.ANDROID_VR -> {
                                put("androidSdkVersion", ANDROID_VR_SDK_VERSION)
                                put("osName", "Android")
                                put("osVersion", ANDROID_VR_OS_VERSION)
                                put("deviceMake", "Oculus")
                                put("deviceModel", "Quest 3")
                            }
                            // Checked against its own device fields, and a Vision Pro claiming to
                            // be a Quest is refused before it gets as far as the manifest. The
                            // visitor session is the part this identity will not go without: with
                            // no visitorData it answers LOGIN_REQUIRED for every video, live or
                            // not, and the manifest never gets minted at all.
                            InnerTubeVariant.VISIONOS -> {
                                put("osName", "visionOS")
                                put("osVersion", VISIONOS_OS_VERSION)
                                put("deviceMake", "Apple")
                                put("deviceModel", VISIONOS_DEVICE_MODEL)
                                put("userAgent", InnerTubeVariant.VISIONOS.userAgent)
                                visitorId?.let { put("visitorData", it) }
                            }
                        }
                    }
                }
                if (isWeb) {
                    putJsonObject("playbackContext") {
                        putJsonObject("contentPlaybackContext") {
                            put("html5Preference", "HTML5_PREF_WANTS")
                            put("referer", "$YT_MUSIC_ORIGIN/")
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
            visitorIdOverride = if (isWeb) sessionId else visitorId
        ).mapCatching { body ->
            // A response can be HTTP 200 while still refusing to play (playabilityStatus != OK), so
            // success has to be judged by that field rather than by the HTTP status — otherwise a
            // refusal never reaches the fallback above.
            // Live first: a livestream legitimately has no format list, so asking for one and
            // treating its absence as a failure would reject a video that plays perfectly well.
            val hls = body.hlsManifestUrl()
            if (hls != null) {
                PlayerResponse(null, variant, poToken?.streamingDataPoToken, hls)
            } else {
                val format = body.bestAudioFormat()
                    ?: throw IOException("YouTube Music returned no playable audio for this track")
                PlayerResponse(format, variant, poToken?.streamingDataPoToken)
            }
        }
    }

    /**
     * The account's own home feed. `FEmusic_home` is the browse id behind music.youtube.com's
     * front page, so this returns exactly the shelves YouTube Music would show that user.
     */
    suspend fun home(): Result<JsonObject> = browse(HOME_BROWSE_ID)

    /**
     * The display name of the signed-in account, or null if the response does not carry one.
     *
     * `account/account_menu` is what the web player asks to draw its own avatar menu, so it
     * answers for exactly the identity the cookie represents — which is the point: a Google
     * account can hold several YouTube channels, and only the session knows which one is active.
     *
     * The name only; the email sitting beside it in the same response is deliberately not read.
     * Settings needs to answer "signed in as who", and an address is more than that question
     * asks for on a screen anyone glancing over a shoulder can see.
     */
    suspend fun accountName(): Result<String?> = post(
        "account/account_menu",
        buildJsonObject { put("context", webContext()) }
    ).map { body -> body.activeAccountName() }

    /**
     * [params] is the opaque blob a "more" button carries alongside its browse id. Browsing with
     * it returns the *full* shelf — an artist's whole discography rather than the handful of tiles
     * their page happens to show. Absent, this is an ordinary browse.
     */
    suspend fun browse(browseId: String, params: String? = null): Result<JsonObject> = post(
        "browse",
        buildJsonObject {
            put("context", webContext())
            put("browseId", browseId)
            params?.takeIf { it.isNotBlank() }?.let { put("params", it) }
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
     * identity, authenticated with a `SAPISIDHASH` computed over [YT_MUSIC_ORIGIN] — Google re-derives the
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

        // The livestream identity carries its visitor session and nothing else — no cookie, no
        // origin, no SAPISID. It is an anonymous YouTube client, not a signed-in music one, and
        // sending it the music host's credentials would make it read as a mismatched identity.
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
        val SAPISID_REGEX = Regex("(?:__Secure-3PAPISID|SAPISID)=([^;]+)")

        fun sapisidHash(sapisid: String): String {
            val time = System.currentTimeMillis() / 1000
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$time $sapisid $YT_MUSIC_ORIGIN".toByteArray(Charsets.UTF_8))
            return "SAPISIDHASH ${time}_${digest.joinToString("") { "%02x".format(it) }}"
        }
    }
}
