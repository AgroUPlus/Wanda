package com.wander.android.data.sources.deezer

import com.wander.android.core.playback.DeezerDecryptingDataSource
import com.wander.android.data.sources.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves full Deezer track CDN streams via Deezer's web gateway and media APIs.
 *
 * Requests the appropriate audio format (MP3_128 for Free users, MP3_320/FLAC for Premium/HiFi)
 * and stamps the track ID header so [DeezerDecryptingDataSource] can decrypt it on the fly.
 */
@Singleton
class DeezerStreamResolver @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val accountManager: DeezerAccountManager
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val sessionMutex = Mutex()
    private var cachedLicenseToken: String? = null

    suspend fun resolveStream(trackId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("deezer:")
        val arl = accountManager.arl
        if (arl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Streaming from Deezer requires signing in"))
        }

        val licenseToken = getOrFetchLicenseToken(arl).getOrElse {
            return@withContext Result.failure(it)
        }

        val trackToken = fetchTrackToken(cleanId, arl).getOrElse {
            return@withContext Result.failure(it)
        }

        val tier = accountManager.tier
        val (requestedFormat, mimeType, bitRate) = selectFormat(tier, accountManager.audioQuality)

        val streamUrl = requestMediaUrl(licenseToken, trackToken, requestedFormat).getOrElse {
            return@withContext Result.failure(it)
        }

        Result.success(
            StreamInfo(
                uri = streamUrl,
                format = mimeType,
                bitRateKbps = bitRate,
                isDirectFile = false,
                headers = mapOf(
                    DeezerDecryptingDataSource.DEEZER_TRACK_ID_HEADER to cleanId
                )
            )
        )
    }

    private fun selectFormat(tier: DeezerAccountTier, userPref: String): Triple<String, String, Int> {
        return when {
            tier == DeezerAccountTier.HIFI && (userPref == "FLAC" || userPref == "AUTO") ->
                Triple("FLAC", "audio/flac", 1411)
            tier.canStreamHq && (userPref == "MP3_320" || userPref == "AUTO") ->
                Triple("MP3_320", "audio/mpeg", 320)
            else ->
                Triple("MP3_128", "audio/mpeg", 128)
        }
    }

    private suspend fun getOrFetchLicenseToken(arl: String): Result<String> = sessionMutex.withLock {
        cachedLicenseToken?.let { return Result.success(it) }

        val request = Request.Builder()
            .url("$GW_LIGHT_URL?method=deezer.getUserData&api_version=1.0&api_token=")
            .addHeader("Cookie", "arl=$arl")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to fetch user data: ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty user data response")
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonObject ?: throw IOException("Invalid user data payload")

                val options = results["OPTIONS"]?.jsonObject
                val licenseToken = options?.get("license_token")?.jsonPrimitive?.content
                    ?: throw IOException("No license token in Deezer user data")

                val isLossless = options["web_lossless"]?.jsonPrimitive?.booleanOrNull == true
                val isHq = options["web_hq"]?.jsonPrimitive?.booleanOrNull == true
                accountManager.updateTier(DeezerAccountTier.fromFlags(isLossless, isHq))

                results["USER"]?.jsonObject?.get("BLOG_NAME")?.jsonPrimitive?.content?.let {
                    accountManager.updateAccountName(it)
                }

                cachedLicenseToken = licenseToken
                licenseToken
            }
        }
    }

    private fun fetchTrackToken(cleanTrackId: String, arl: String): Result<String> {
        val payload = buildJsonObject {
            put("sng_id", cleanTrackId)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$GW_LIGHT_URL?method=song.getData&api_version=1.0&api_token=")
            .addHeader("Cookie", "arl=$arl")
            .post(payload)
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to fetch song data: ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty song data response")
                val root = json.parseToJsonElement(body).jsonObject
                val results = root["results"]?.jsonObject ?: throw IOException("No results in song.getData")
                results["TRACK_TOKEN"]?.jsonPrimitive?.content
                    ?: throw IOException("No TRACK_TOKEN found for track $cleanTrackId")
            }
        }
    }

    private fun requestMediaUrl(licenseToken: String, trackToken: String, format: String): Result<String> {
        val bodyJson = buildJsonObject {
            put("license_token", licenseToken)
            putJsonArray("media") {
                addJsonObject {
                    put("type", "FULL")
                    putJsonArray("formats") {
                        addJsonObject {
                            put("cipher", "BF_CBC_STRIPE")
                            put("format", format)
                        }
                    }
                }
            }
            putJsonArray("track_tokens") {
                add(trackToken)
            }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(MEDIA_URL)
            .post(bodyJson)
            .build()

        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Media URL request failed: ${response.code}")
                val body = response.body?.string() ?: throw IOException("Empty media URL response")
                val root = json.parseToJsonElement(body).jsonObject
                val dataArray = root["data"]?.jsonArray ?: throw IOException("Missing data in media URL response")
                val firstMedia = dataArray.firstOrNull()?.jsonObject?.get("media")?.jsonArray?.firstOrNull()?.jsonObject
                val sources = firstMedia?.get("sources")?.jsonArray
                val url = sources?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                    ?: throw IOException("No stream URL available in requested format $format")
                url
            }
        }
    }

    fun invalidateSession() {
        cachedLicenseToken = null
    }

    private companion object {
        const val GW_LIGHT_URL = "https://www.deezer.com/ajax/gw-light.php"
        const val MEDIA_URL = "https://media.deezer.com/v1/get_url"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
