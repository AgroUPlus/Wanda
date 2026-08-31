package com.wander.android.data.sources.agro

import android.content.Context
import android.net.Uri
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.security.AudioStreamCipher
import com.wander.android.core.security.AudioStreamKeys
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.RelayStreamFraming
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles ephemeral server relay audio streaming for Listen Along and Jam sessions.
 */
@Singleton
class AgroRelayClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val trackDao: TrackDao,
    private val identityKeyManager: IdentityKeyManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val base: String? get() = secureStorage.agroServerUrl.trimEnd('/').takeIf { it.isNotBlank() }
    private val apiKey get() = secureStorage.agroApiKey

    private val relayClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Opens an ephemeral relay session on Agro and returns the streaming URL for the receiver.
     */
    suspend fun openRelayReceiveStream(
        fromDevice: String,
        toDevice: String,
        contentHash: String
    ): Result<String> = runCatching {
        val server = base ?: throw IOException("Agro server not configured")
        // Our identity key travels with the request so the host can seal a room key to it. The
        // server passes it through and never uses it.
        val listenerKey = runCatching { identityKeyManager.getPublicKeyBase64() }.getOrNull()
        val json = buildString {
            append("""{"contentHash":"$contentHash","fromDevice":"$fromDevice","toDevice":"$toDevice"""")
            if (!listenerKey.isNullOrBlank()) append(""","listenerPublicKey":"$listenerKey"""")
            append("}")
        }
        val req = Request.Builder()
            .url("$server/api/v1/relay/open")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        val response = relayClient.newCall(req).execute()
        response.use { res ->
            if (!res.isSuccessful) {
                throw IOException("Relay open refused: HTTP ${res.code}")
            }
            val bodyStr = res.body.string()
            val parsed = JSON.parseToJsonElement(bodyStr) as JsonObject
            val sessionId = parsed["sessionId"]?.jsonPrimitive?.content
                ?: throw IOException("No sessionId in relay response")

            "$server/api/v1/relay/$sessionId/receive"
        }
    }

    /**
     * Called when this device receives a RELAY_REQUEST message over WebSocket.
     * Streams the local audio file to the relay pipe at POST /api/v1/relay/{sessionId}/send.
     */
    fun handleRelayRequest(
        sessionId: String,
        contentHash: String,
        listenerPublicKey: String? = null
    ) {
        scope.launch {
            val server = base ?: return@launch
            val track = trackDao.findByContentHash(contentHash) ?: return@launch

            val (inputProvider, size) = when {
                track.localFilePath != null && File(track.localFilePath).exists() -> {
                    val file = File(track.localFilePath)
                    Pair({ file.inputStream() }, file.length())
                }
                track.streamUri != null -> {
                    val uri = Uri.parse(track.streamUri)
                    // Size comes from the file descriptor, not from `available()` on a probe
                    // stream: `available()` reports what can be read without blocking rather than
                    // the length, and the probe stream was being opened and never closed.
                    val size = runCatching {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                    }.getOrNull() ?: -1L
                    val canOpen = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { true } ?: false
                    }.getOrDefault(false)
                    if (canOpen) {
                        Pair({ context.contentResolver.openInputStream(uri)!! }, size)
                    } else null
                }
                else -> null
            } ?: return@launch

            // One key per track, sealed to the listener alone. Absent a listener key there is
            // nobody to seal to, and encrypting would send bytes only this device could read.
            val roomKey = listenerPublicKey?.takeIf { it.isNotBlank() }?.let { AudioStreamKeys.newRoomKey() }
            val sealedKey = roomKey?.let { key ->
                runCatching {
                    identityKeyManager.sealNote(listenerPublicKey!!, AudioStreamKeys.encodeRoomKey(key))
                }.getOrNull()
            }

            val requestBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()

                // Framing and tags change the length, and the count is unknown until the last
                // chunk is sealed. Chunked is honest; a wrong length would truncate the audio.
                override fun contentLength() = if (sealedKey == null && size > 0) size else -1L

                override fun writeTo(sink: BufferedSink) {
                    inputProvider().use { input ->
                        if (sealedKey == null || roomKey == null) {
                            sink.writeAll(input.source())
                        } else {
                            RelayStreamFraming.encrypt(
                                input,
                                sink.outputStream(),
                                AudioStreamCipher(AudioStreamKeys.derive(roomKey, sessionId))
                            )
                        }
                    }
                }
            }

            val req = Request.Builder()
                .url("$server/api/v1/relay/$sessionId/send")
                .header("Authorization", "Bearer $apiKey")
                .apply {
                    if (sealedKey != null) {
                        header("x-agro-encrypted", "true")
                        header("x-agro-sealed-key", sealedKey)
                    }
                }
                .post(requestBody)
                .build()

            runCatching {
                relayClient.newCall(req).execute().use { res ->
                    android.util.Log.i(TAG, "Relay send outcome: HTTP ${res.code}")
                }
            }.onFailure { err ->
                android.util.Log.w(TAG, "Relay send failed: ${err.message}")
            }
        }
    }

    private companion object {
        const val TAG = "AgroRelayClient"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
