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
        fetchDirect(track)
            ?: fetchViaRelay(track)
            ?: fetchFromArchive(track)
    }

    /** 1. Direct LAN P2P: the first reachable peer with a LAN address wins. */
    private fun fetchDirect(track: MissingTrack): FetchedStream? {
        for (source in track.peerSources) {
            val lan = source.lanAddress ?: continue
            try {
                android.util.Log.i("P2P", "Attempting direct LAN P2P fetch from $lan for \"${track.title}\"")
                val p2pClient = uploadClient.newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val response = p2pClient.newCall(authorized("http://$lan/p2p/fetch/${track.contentHash}").get().build()).execute()
                if (response.isSuccessful && response.body != null) {
                    android.util.Log.i("P2P", "Direct LAN P2P streaming connected from $lan for \"${track.title}\"")
                    return FetchedStream(response, SyncRoute.DIRECT)
                }
                response.close()
            } catch (e: IOException) {
                android.util.Log.w("P2P", "LAN P2P connection to $lan failed: ${e.javaClass.simpleName}")
            } catch (e: IllegalArgumentException) {
                // A peer advertising a LAN address OkHttp cannot build a URL from.
                android.util.Log.w("P2P", "LAN P2P address $lan is not usable")
            }
        }
        return null
    }

    /** 2. Ephemeral server relay through the first peer that is not the server archive. */
    private fun fetchViaRelay(track: MissingTrack): FetchedStream? {
        val remotePeer = track.peerSources.firstOrNull { !it.isServerArchive } ?: return null
        android.util.Log.i("P2P", "Relaying \"${track.title}\" via ${remotePeer.petname}")
        return try {
            openRelaySession(track, remotePeer)?.let { receiveRelay(it) }
        } catch (error: IOException) {
            // Swallowed silently until now, which is why a relay that never worked looked
            // exactly like one that was never tried. It still falls through to the archive —
            // this only makes the reason visible.
            android.util.Log.w("P2P", "Relay failed for \"${track.title}\": ${error.javaClass.simpleName}")
            null
        } catch (error: IllegalArgumentException) {
            // The relay's open response was not the JSON object it should be.
            android.util.Log.w("P2P", "Relay answered something unreadable for \"${track.title}\"")
            null
        }
    }

    private fun openRelaySession(track: MissingTrack, remotePeer: PeerSource): String? {
        val openBody = buildJsonObject {
            put("contentHash", track.contentHash)
            put("fromDevice", remotePeer.deviceId)
            put("toDevice", secureStorage.agroDeviceId)
        }.toString().toRequestBody("application/json".toMediaType())

        android.util.Log.i("P2P", "Opening a relay session …")
        val openRes = relayClient.newCall(authorized("$base/api/v1/relay/open").post(openBody).build()).execute()
        android.util.Log.i("P2P", "Relay open answered HTTP ${openRes.code}")
        if (!openRes.isSuccessful) {
            android.util.Log.w("P2P", "Relay open refused: HTTP ${openRes.code}")
            openRes.close()
            return null
        }
        val bodyStr = openRes.body.string()
        openRes.close()
        val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(bodyStr) as? JsonObject
        val sessionId = jsonObj?.get("sessionId")?.jsonPrimitive?.contentOrNull
        if (sessionId == null) android.util.Log.w("P2P", "Relay open returned no session id")
        return sessionId
    }

    private fun receiveRelay(sessionId: String): FetchedStream? {
        android.util.Log.i("P2P", "Receiving from the relay session …")
        val recvRes = relayClient.newCall(authorized("$base/api/v1/relay/$sessionId/receive").get().build()).execute()
        if (recvRes.isSuccessful && recvRes.body != null) {
            android.util.Log.i("P2P", "Relay stream open")
            return FetchedStream(recvRes, SyncRoute.RELAY)
        }
        android.util.Log.w("P2P", "Relay receive refused: HTTP ${recvRes.code}")
        recvRes.close()
        return null
    }

    /** 3. Server archive fallback (only if server actually holds the file). */
    private fun fetchFromArchive(track: MissingTrack): FetchedStream {
        if (track.peerSources.none { it.isServerArchive }) {
            throw IOException(
                "Couldn't reach any device holding \"${track.title}\" — tried the local network " +
                    "and the relay."
            )
        }
        android.util.Log.i("P2P", "Falling back to the server archive for \"${track.title}\"")
        return FetchedStream(fetch(track.contentHash).getOrThrow(), SyncRoute.ARCHIVE)
    }

    private fun authorized(url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer ${secureStorage.agroApiKey}")

    private companion object {
        const val RELAY_STALL_TIMEOUT_SECONDS = 45L
    }
}
