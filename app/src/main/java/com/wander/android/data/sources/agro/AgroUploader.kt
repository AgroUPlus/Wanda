package com.wander.android.data.sources.agro

import android.content.Context
import androidx.core.net.toUri
import com.wander.android.core.database.entity.TrackEntity
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What happened to one file. */
sealed interface UploadOutcome {
    /** The server already had these bytes. Nothing was transferred. */
    data object AlreadyPresent : UploadOutcome
    data object Uploaded : UploadOutcome
    /** Sent as far as it got. The next attempt resumes rather than restarting. */
    data class Partial(val received: Long) : UploadOutcome
    data class Failed(val reason: String) : UploadOutcome
}

/**
 * Moves audio files to Agro over its REST upload routes.
 *
 * Not through [AgroGraphQl]: these are megabytes, and a GraphQL envelope would both inflate them
 * (base64) and force the whole file into memory. The protocol is deliberately resumable — a phone
 * on Wi-Fi drops mid-transfer routinely, and restarting a 40 MB FLAC from zero every time means a
 * large library never finishes.
 */
@Singleton
class AgroUploader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    okHttpClient: OkHttpClient
) {

    /**
     * A separate client, not the shared one.
     *
     * The shared instance sets a 15-second write timeout, which is right for API calls and fatal
     * for a large upload on a weak connection. Timeouts are removed here rather than raised
     * globally, so nothing else in the app loses its deadline. Connection pool and dispatcher are
     * still shared — `newBuilder` reuses them.
     */
    private val uploadClient: OkHttpClient = okHttpClient.newBuilder()
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /**
     * The same client, but it gives up.
     *
     * Uploading a library is measured in hours, so [uploadClient] waits forever on purpose. A
     * relay is the opposite: it depends on a *second* device answering a push notification, and
     * when that device does not answer there is nothing to wait for. With no timeout the fetch sat
     * on an open socket indefinitely, showing "Syncing…" and never failing — which is
     * indistinguishable from working, and is why a relay that never delivered a byte looked like a
     * slow one.
     *
     * The read timeout is what bounds the wait for the *first* byte. Once bytes are flowing the
     * transfer is a plain download and the timeout only fires if the stream stalls for that long.
     */
    private val relayClient: OkHttpClient = okHttpClient.newBuilder()
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(RELAY_STALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val base: String get() = secureStorage.agroServerUrl.trimEnd('/')

    suspend fun upload(track: TrackEntity): UploadOutcome = withContext(Dispatchers.IO) {
        val hash = track.contentHash
            ?: return@withContext UploadOutcome.Failed("not hashed yet")
        val uri = track.streamUri
            ?: return@withContext UploadOutcome.Failed("no local file")
        val size = track.sizeBytes
            ?: return@withContext UploadOutcome.Failed("unknown size")

        val begun = begin(track, hash, size).getOrElse {
            return@withContext UploadOutcome.Failed(it.message ?: "could not start the upload")
        }
        when (begun) {
            is Begin.Exists -> UploadOutcome.AlreadyPresent
            is Begin.Upload -> sendBytes(begun.uploadId, uri, begun.offset, size)
        }
    }

    // ── Step one: declare the file ──────────────────────────────────────────────────────────

    private sealed interface Begin {
        data object Exists : Begin
        data class Upload(val uploadId: String, val offset: Long) : Begin
    }

    /**
     * Declares the file and asks whether it needs sending.
     *
     * The common answer once a library has been uploaded once is "already have it", which costs a
     * single small request instead of the whole file — by far the biggest saving in the feature.
     */
    private fun begin(track: TrackEntity, hash: String, size: Long): Result<Begin> {
        val payload = buildJsonObject {
            put("deviceId", secureStorage.agroDeviceId)
            put("contentHash", hash)
            put("sizeBytes", size)
            put("title", track.title)
            put("artist", track.artist)
            track.album?.let { put("album", it) }
            track.albumArtist?.let { put("albumArtist", it) }
            track.trackNumber?.let { put("trackNo", it) }
            track.discNumber?.let { put("discNo", it) }
            track.year?.let { put("year", it) }
            track.genre?.let { put("genre", it) }
            put("durationMs", track.durationMs)
            track.format?.let { put("format", it) }
            track.bitRateKbps?.let { put("bitrateKbps", it) }
            track.streamUri?.let { put("localRef", it) }
            track.fileExtension?.let { put("extension", it) }
        }

        val request = Request.Builder()
            .url("$base/api/v1/library/upload")
            .header("Authorization", "Bearer ${secureStorage.agroApiKey}")
            .post(payload.toString().toRequestBody())
            .build()

        return runCatching {
            uploadClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw IOException("the server refused the upload (HTTP ${response.code})")
                }
                val json = HTTP_JSON.parseToJsonElement(body) as? JsonObject
                    ?: throw IOException("the server sent an unreadable reply")
                when (json["status"]?.jsonPrimitive?.contentOrNull) {
                    "exists" -> Begin.Exists
                    "upload" -> Begin.Upload(
                        uploadId = json["uploadId"]?.jsonPrimitive?.contentOrNull
                            ?: throw IOException("the server sent no upload id"),
                        offset = json["offset"]?.jsonPrimitive?.long ?: 0L
                    )
                    else -> throw IOException("the server sent an unexpected status")
                }
            }
        }
    }

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
                    val bodyStr = openRes.body?.string().orEmpty()
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

    // ── Step two: send the bytes ────────────────────────────────────────────────────────────

    private fun sendBytes(
        uploadId: String,
        uriString: String,
        offset: Long,
        size: Long
    ): UploadOutcome {
        val request = Request.Builder()
            .url("$base/api/v1/library/upload/$uploadId")
            .header("Authorization", "Bearer ${secureStorage.agroApiKey}")
            .header("x-agro-offset", offset.toString())
            .put(ContentUriBody(uriString, offset, size))
            .build()

        return runCatching {
            uploadClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    return@use UploadOutcome.Failed("HTTP ${response.code}")
                }
                val json = HTTP_JSON.parseToJsonElement(body) as? JsonObject
                when (json?.get("status")?.jsonPrimitive?.contentOrNull) {
                    "archived", "spooled" -> UploadOutcome.Uploaded
                    "partial" -> UploadOutcome.Partial(
                        json["received"]?.jsonPrimitive?.long ?: 0L
                    )
                    else -> UploadOutcome.Failed("unexpected reply from the server")
                }
            }
        }.getOrElse { UploadOutcome.Failed(it.message ?: "the transfer failed") }
    }

    /**
     * Streams a media file straight from its content URI into the request.
     *
     * Deliberately not `ByteArray`: the app has a normal heap and libraries contain 40 MB FLACs.
     * `okio` copies through a fixed buffer, so memory stays flat whatever the file size — the same
     * property the server side has.
     */
    private inner class ContentUriBody(
        private val uriString: String,
        private val offset: Long,
        private val size: Long
    ) : RequestBody() {

        override fun contentType() = "application/octet-stream".toMediaType()

        /** Known up front, so OkHttp sets Content-Length instead of chunking. */
        override fun contentLength(): Long = (size - offset).coerceAtLeast(0)

        override fun writeTo(sink: BufferedSink) {
            val stream = context.contentResolver.openInputStream(uriString.toUri())
                ?: throw IOException("the file is no longer readable")
            stream.use { input ->
                // Resuming: skip what the server already holds rather than re-sending it.
                var remaining = offset
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped <= 0) throw IOException("could not resume from offset $offset")
                    remaining -= skipped
                }
                sink.writeAll(input.source())
            }
        }
    }

    private fun String.toRequestBody() =
        toRequestBody("application/json".toMediaType())

    private companion object {

        /** How long a relay may deliver nothing before it is called a failure. */

        const val RELAY_STALL_TIMEOUT_SECONDS = 45L

        val HTTP_JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
