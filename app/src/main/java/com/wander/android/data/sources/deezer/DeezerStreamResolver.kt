package com.wander.android.data.sources.deezer

import com.wander.android.core.playback.DeezerDecryptingDataSource
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves full Deezer track CDN streams via Deezer's web gateway and media APIs.
 *
 * Asks for the best format the account and the user's preference allow, falling back down
 * [DeezerAudioFormat]'s ladder when this track has no file at that quality, and stamps the track ID
 * header so [DeezerDecryptingDataSource] can decrypt it on the fly.
 */
@Singleton
class DeezerStreamResolver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val accountManager: DeezerAccountManager,
    private val gateway: DeezerGateway
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun resolveStream(trackId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("deezer:")
        val arl = accountManager.arl
        if (arl.isBlank()) {
            invalidateSession()
            return@withContext Result.failure(IllegalStateException("Streaming from Deezer requires signing in"))
        }

        var session = gateway.session(arl).getOrElse { return@withContext Result.failure(it) }
        var trackToken = gateway.trackToken(session, cleanId)
        // A gateway session goes stale on Deezer's side long before the process ends; one fresh
        // session is worth trying before calling the track unplayable.
        if ((trackToken.exceptionOrNull() as? DeezerGateway.GatewayException)?.isSessionExpired == true) {
            gateway.invalidate()
            session = gateway.session(arl).getOrElse { return@withContext Result.failure(it) }
            trackToken = gateway.trackToken(session, cleanId)
        }
        val token = trackToken.getOrElse { return@withContext Result.failure(it) }

        val ladder = DeezerAudioFormat.ladderFrom(accountManager.tier, accountManager.audioQuality)
        requestMediaUrl(session.licenseToken, token, ladder).map { (streamUrl, format) ->
            StreamInfo(
                uri = streamUrl,
                format = format.mimeType,
                bitRateKbps = format.bitRateKbps,
                isDirectFile = false,
                headers = mapOf(DeezerDecryptingDataSource.DEEZER_TRACK_ID_HEADER to cleanId)
            )
        }
    }

    /**
     * Trades the tokens for a CDN URL. Every format in [ladder] is offered, best first, and Deezer
     * answers with the first one this track actually has — a song with no FLAC master plays as
     * MP3 instead of failing for a HiFi account. The format it picked is read back, because the
     * player and the cache key need to know what the bytes really are.
     */
    private fun requestMediaUrl(
        licenseToken: String,
        trackToken: String,
        ladder: List<DeezerAudioFormat>
    ): Result<Pair<String, DeezerAudioFormat>> {
        val bodyJson = buildJsonObject {
            put("license_token", licenseToken)
            putJsonArray("media") {
                addJsonObject {
                    put("type", "FULL")
                    putJsonArray("formats") {
                        ladder.forEach { format ->
                            addJsonObject {
                                put("cipher", "BF_CBC_STRIPE")
                                put("format", format.tag)
                            }
                        }
                    }
                }
            }
            putJsonArray("track_tokens") { add(trackToken) }
        }.toString().toRequestBody(DeezerGateway.JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(MEDIA_URL)
            .post(bodyJson)
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Media URL request failed: ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty media URL response")
                val root = json.parseToJsonElement(body).jsonObject
                mediaErrors(root["errors"])?.let { throw IOException("Deezer refused the stream: $it") }
                val entry = root["data"]?.jsonArray?.firstOrNull() as? JsonObject
                    ?: throw IOException("Missing data in media URL response")
                mediaErrors(entry["errors"])?.let { throw IOException("Deezer refused the stream: $it") }
                val media = entry["media"]?.jsonArray?.firstOrNull() as? JsonObject
                    ?: throw IOException("Deezer has no stream of this track in ${ladder.joinToString { it.tag }}")
                val url = (media["sources"]?.jsonArray?.firstOrNull() as? JsonObject)
                    ?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: throw IOException("No stream URL in Deezer media response")
                val served = media["format"]?.jsonPrimitive?.contentOrNull
                    ?.let(DeezerAudioFormat::fromTag)
                    ?: ladder.first()
                url to served
            }
        }
    }

    /** The messages in a media API `errors` array, or null when it reports none. */
    private fun mediaErrors(errors: JsonElement?): String? =
        (errors as? JsonArray)
            ?.map { error ->
                val fields = error as? JsonObject
                fields?.get("message")?.jsonPrimitive?.contentOrNull
                    ?: fields?.get("code")?.jsonPrimitive?.contentOrNull
                    ?: error.toString()
            }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("; ")

    fun invalidateSession() = gateway.invalidate()

    private companion object {
        const val MEDIA_URL = "https://media.deezer.com/v1/get_url"
    }
}
