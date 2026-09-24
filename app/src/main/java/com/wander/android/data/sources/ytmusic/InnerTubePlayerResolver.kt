package com.wander.android.data.sources.ytmusic

import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A resolved playable audio format paired with the client identity that produced it. */
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
 * Resolves playable stream formats and manifests using ANDROID_VR, WEB_REMIX, and VISIONOS variants.
 */
@Singleton
internal class InnerTubePlayerResolver @Inject constructor(
    private val accountManager: GoogleAccountManager,
    private val transport: InnerTubeTransport
) {
    /** Pairs the PO Token request with the player request when no visitor ID is available yet. */
    private val fallbackSessionId = UUID.randomUUID().toString()

    private val visitorLock = Mutex()
    @Volatile private var cachedVisitorId: String? = null

    private suspend fun visitorSession(): String? {
        accountManager.visitorData.takeIf { it.isNotBlank() }?.let { return it }
        cachedVisitorId?.let { return it }
        return visitorLock.withLock {
            cachedVisitorId ?: fetchVisitorId()?.also { cachedVisitorId = it }
        }
    }

    private suspend fun fetchVisitorId(): String? = transport.post(
        "visitor_id",
        buildJsonObject { put("context", transport.webContext()) },
        client = InnerTubeVariant.WEB_REMIX
    ).getOrNull()?.visitorData()

    suspend fun resolvePlayer(videoId: String): Result<PlayerResponse> {
        val vr = playerAs(videoId, InnerTubeVariant.ANDROID_VR)
        vr.getOrNull()?.takeIf { it.hlsManifestUrl == null }?.let { return Result.success(it) }

        val web = playerAs(videoId, InnerTubeVariant.WEB_REMIX)
        web.getOrNull()?.takeIf { it.hlsManifestUrl == null }?.let { return Result.success(it) }

        val live = playerAs(videoId, InnerTubeVariant.VISIONOS)
        live.getOrNull()?.let { return Result.success(it) }

        vr.getOrNull()?.let { return Result.success(it) }
        web.getOrNull()?.let { return Result.success(it) }

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
        val visitorId = if (isLive) visitorSession() else null
        val sessionId = accountManager.visitorData.ifBlank { fallbackSessionId }
        val poToken = if (isWeb) {
            runCatching { PoTokenGenerator().getWebClientPoToken(videoId, sessionId) }.getOrNull()
        } else {
            null
        }
        val signatureTimestamp = if (isWeb) {
            runCatching { CipherDeobfuscator.signatureTimestamp() }.getOrNull()
        } else {
            null
        }

        return transport.post(
            "player",
            buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", variant.contextClientName)
                        put("clientVersion", variant.clientVersion)
                        put("hl", transport.deviceLanguage())
                        put("gl", transport.deviceCountry())
                        when (variant) {
                            InnerTubeVariant.WEB_REMIX -> put("visitorData", sessionId)
                            InnerTubeVariant.ANDROID_VR -> {
                                put("androidSdkVersion", ANDROID_VR_SDK_VERSION)
                                put("osName", "Android")
                                put("osVersion", ANDROID_VR_OS_VERSION)
                                put("deviceMake", "Oculus")
                                put("deviceModel", "Quest 3")
                            }
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
}
