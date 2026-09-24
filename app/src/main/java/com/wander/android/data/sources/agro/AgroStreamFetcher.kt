package com.wander.android.data.sources.agro

import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles fetching audio streams over direct local LAN P2P, stateless ephemeral server relay,
 * or the server's permanent archive.
 */
@Singleton
class AgroStreamFetcher @Inject constructor(
    private val secureStorage: SecureStorage,
    okHttpClient: OkHttpClient
) {
    private val uploadClient: OkHttpClient = okHttpClient.newBuilder()
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val relayClient: OkHttpClient = okHttpClient.newBuilder()
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(RELAY_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val base: String get() = secureStorage.agroServerUrl.trimEnd('/')

    /**
     * Downloads a track the server holds.
     *
     * Hands back the raw stream rather than bytes: the caller writes it straight into MediaStore,
     * so a 40 MB file never lands in the heap on its way past.
     *
     * The response body must be closed by the caller — it holds a live connection.
     */
    fun fetch(contentHash: String): Result<okhttp3.Response> = runCatching {
        val request = Request.Builder()
            .url("$base/api/v1/library/fetch/$contentHash")
            .header("Authorization", "Bearer ${secureStorage.agroApiKey}")
            .get()
            .build()
        val response = uploadClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("the server would not hand that file over (HTTP ${response.code})")
        }
        response
    }

    /**
     * Downloads a track over direct local LAN P2P or stateless ephemeral server relay before falling
     * back to the server's permanent archive.
     */
    fun fetchP2POrRelay(track: MissingTrack): Result<FetchedStream> = runCatching {
        // 1. Direct LAN P2P
        for (source in track.peerSources) {
            val lan = source.lanAddress ?: continue
            try {
                android.util.Log.i("P2P", "Attempting direct LAN P2P fetch from $lan for \"${track.title}\"")
                val p2pClient = uploadClient.newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url("http://$lan/p2p/fetch/${track.contentHash}")
                    .header("Authorization", "Bearer ${secureStorage.agroApiKey}")
                    .get()
                    .build()
                val response = p2pClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    android.util.Log.i("P2P", "Direct LAN P2P streaming connected from $lan for \"${track.title}\"")
                    return@runCatching FetchedStream(response, SyncRoute.DIRECT)
                }
                response.close()
            } catch (e: Exception) {
                android.util.Log.w("P2P", "LAN P2P connection to $lan failed: ${e.message}")
            }
        }

        // 2. Ephemeral server relay
        val remotePeer = track.peerSources.firstOrNull { !it.isServerArchive }
        if (remotePeer != null) {
            android.util.Log.i("P2P", "Relaying \"${track.title}\" via ${remotePeer.petname}")
            try {
                val openBody = buildJsonObject {
                    put("contentHash", track.contentHash)
                    put("fromDevice", remotePeer.deviceId)
                    put("toDevice", secureStorage.agroDeviceId)
                }.toString().toRequestBody("application/json".toMediaType())

                val openReq = Request.Builder()
                    .url("$base/api/v1/relay/open")
                    .header("Authorization", "Bearer ${secureStorage.agroApiKey}")
                    .post(openBody)
                    .build()

                android.util.Log.i("P2P", "POST $base/api/v1/relay/open …")
                val openRes = relayClient.newCall(openReq).execute()
                android.util.Log.i("P2P", "Relay open answered HTTP ${openRes.code}")
                if (openRes.isSuccessful) {
                    val bodyStr = openRes.body.string()
                    openRes.close()
                    val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(bodyStr) as? JsonObject
                    val sessionId = jsonObj?.get("sessionId")?.jsonPrimitive?.contentOrNull
                    if (sessionId == null) {
                        android.util.Log.w("P2P", "Relay open returned no session id: $bodyStr")
                    }
                    if (sessionId != null) {
                        val recvReq = Request.Builder()
                            .url("$base/api/v1/relay/$sessionId/receive")
                            .header("Authorization", "Bearer ${secureStorage.agroApiKey}")
                            .get()
                            .build()
                        android.util.Log.i("P2P", "GET relay/$sessionId/receive …")
                        val recvRes = relayClient.newCall(recvReq).execute()
                        if (recvRes.isSuccessful && recvRes.body != null) {
                            android.util.Log.i("P2P", "Relay stream open (session $sessionId)")
                            return@runCatching FetchedStream(recvRes, SyncRoute.RELAY)
                        }
                        android.util.Log.w("P2P", "Relay receive refused: HTTP ${recvRes.code}")
                        recvRes.close()
                    }
                } else {
                    android.util.Log.w("P2P", "Relay open refused: HTTP ${openRes.code}")
                    openRes.close()
                }
            } catch (error: Exception) {
                // Swallowed silently until now, which is why a relay that never worked looked
                // exactly like one that was never tried. It still falls through to the archive —
                // this only makes the reason visible.
                android.util.Log.w("P2P", "Relay failed for \"${track.title}\": ${error.message}")
            }
        }

        // 3. Server archive fallback (only if server actually holds the file)
        val serverArchived = track.peerSources.any { it.isServerArchive }
        if (serverArchived) {
            android.util.Log.i("P2P", "Falling back to the server archive for \"${track.title}\"")
            FetchedStream(fetch(track.contentHash).getOrThrow(), SyncRoute.ARCHIVE)
        } else {
            throw IOException(
                "Couldn't reach any device holding \"${track.title}\" — tried the local network " +
                    "and the relay."
            )
        }
    }

    private companion object {
        const val RELAY_STALL_TIMEOUT_SECONDS = 45L
    }
}
