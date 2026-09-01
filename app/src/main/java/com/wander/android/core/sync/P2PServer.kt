package com.wander.android.core.sync

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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedded lightweight HTTP server on Android port 8702.
 *
 * Serves audio files directly over the local Wi-Fi network to peer devices (like Wander on Linux)
 * for gigabit-speed LAN transfers without touching server storage or quotas.
 */
@Singleton
class P2PServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val secureStorage: SecureStorage,
    private val identityKeyManager: IdentityKeyManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isRunning = false

    /**
     * Grants Agro has issued for this device, by token.
     *
     * This server listens on every interface, and a LAN is not a trust boundary — a hotel, a
     * campus or a coffee shop is one network in exactly the sense that matters here. Without this
     * anyone on the same Wi-Fi could read the whole library a track at a time, and asking by track
     * id rather than by content hash makes that a guess rather than a search.
     *
     * Agro mints the token, tells this device about it over the WebSocket before handing it to the
     * listener, and drops it when either socket closes, so nothing here has to be persisted or
     * expire on its own.
     */
    private val grants = java.util.concurrent.ConcurrentHashMap<String, Grant>()

    private data class Grant(val forUser: String, val expiresAtMs: Long)

    /** Records a grant pushed by Agro as `P2P_GRANT`. */
    fun acceptGrant(token: String, forUser: String, ttlSeconds: Long) {
        if (token.isBlank()) return
        grants.entries.removeAll { it.value.expiresAtMs <= System.currentTimeMillis() }
        grants[token] = Grant(forUser, System.currentTimeMillis() + ttlSeconds * 1000L)
    }

    /** Drops every grant. Called when the session that justified them ends. */
    fun clearGrants() = grants.clear()

    private fun isAuthorised(request: String): Boolean {
        val token = request.lineSequence()
            .firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.removePrefix("Bearer ")
            ?.trim()
            .orEmpty()
        if (token.isEmpty()) return false
        val grant = grants[token] ?: return false
        if (grant.expiresAtMs <= System.currentTimeMillis()) {
            grants.remove(token)
            return false
        }
        return true
    }

    /**
     * The requester's identity public key, when it sent one.
     *
     * Its presence is what turns encryption on. A peer that does not ask for it gets plaintext, so
     * an older build on the other phone keeps working — but every current build asks, and the
     * resolver has no path that does not.
     */
    private fun identityKeyOf(request: String): String? = request.lineSequence()
        .firstOrNull { it.startsWith(IDENTITY_HEADER, ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    fun start(port: Int = 8702) {
        if (isRunning) return
        isRunning = true

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wanda:p2p")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WifiLock", e)
        }

        scope.launch {
            try {
                val server = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
                serverSocket = server
                Log.i(TAG, "P2PServer listening on port $port")
                while (isRunning && !server.isClosed) {
                    try {
                        val client = server.accept()
                        scope.launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start P2PServer", e)
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            wifiLock?.release()
        } catch (ignored: Exception) {}
        wifiLock = null
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 5000
            val input = s.getInputStream()
            val output = s.getOutputStream()
            val buffer = ByteArray(4096)
            val bytesRead = try {
                input.read(buffer)
            } catch (e: Exception) {
                return
            }
            if (bytesRead <= 0) return

            val request = String(buffer, 0, bytesRead)
            val firstLine = request.lineSequence().firstOrNull() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]
            Log.d(TAG, "Handling P2P request: $method $path")

            if (method == "GET" && path == "/p2p/ping") {
                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\npong"
                output.write(response.toByteArray())
                output.flush()
                return
            }

            if (method == "GET" && (path.startsWith("/p2p/fetch/") || path.startsWith("/p2p/stream"))) {
                // The ping above is deliberately open — it answers "something is listening" and
                // nothing else. Everything that returns audio needs a grant.
                if (!isAuthorised(request)) {
                    val body = "forbidden"
                    output.write(
                        ("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\n" +
                            "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                            .toByteArray()
                    )
                    output.flush()
                    return
                }

                val fetchHash = if (path.startsWith("/p2p/fetch/")) {
                    path.removePrefix("/p2p/fetch/").substringBefore("?")
                } else null

                // Addressed by content hash only. Looking a track up by *id* was a far wider
                // door for no caller: an id is short and guessable where a SHA-256 is neither, and
                // nothing asks this server for one.
                val queryUri = Uri.parse("http://localhost$path")
                val queryHash = queryUri.getQueryParameter("hash") ?: fetchHash
                val track = queryHash?.let { trackDao.findByContentHash(it) }

                val (inputStream, totalLength) = when {
                    track?.localFilePath != null && java.io.File(track.localFilePath).exists() -> {
                        val f = java.io.File(track.localFilePath)
                        Pair(f.inputStream(), f.length())
                    }
                    track?.streamUri != null -> {
                        val uri = Uri.parse(track.streamUri)
                        val s = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                        val len = runCatching {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                        }.getOrNull() ?: -1L
                        if (s != null) Pair(s, len) else Pair(null, -1L)
                    }
                    else -> Pair(null, -1L)
                }

                if (inputStream != null) {
                    try {
                        inputStream.use { fileIn ->
                            val mime = when (track?.format?.lowercase()) {
                                "flac" -> "audio/flac"
                                "opus", "webm" -> "audio/ogg"
                                "m4a", "mp4" -> "audio/mp4"
                                else -> "audio/mpeg"
                            }
                            val recipientKey = identityKeyOf(request)
                            val session = queryUri.getQueryParameter("session").orEmpty()
                            if (recipientKey != null && session.isNotBlank()) {
                                writeEncrypted(output, fileIn, mime, recipientKey, session)
                            } else {
                                writePlain(output, fileIn, mime, totalLength)
                            }
                            return
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed streaming track $path", e)
                    }
                }

                val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\nConnection: close\r\n\r\nNot Found"
                output.write(notFound.toByteArray())
                output.flush()
                return
            }

            val badReq = "HTTP/1.1 400 Bad Request\r\nContent-Length: 11\r\nConnection: close\r\n\r\nBad Request"
            output.write(badReq.toByteArray())
            output.flush()
        }
    }

    /**
     * The same encrypted stream the relay carries, over the local link.
     *
     * A LAN is not a trust boundary — that is already why this server needs a grant — and it is not
     * a confidentiality boundary either. Everything on a hotel or campus network sees these packets,
     * and until now they were the audio itself, in the clear. The relay path was encrypted while the
     * *closer* path was not, which is precisely backwards from what anyone would assume.
     *
     * The key is sealed to the public key the requester sent, so it is readable by that peer and by
     * nothing else on the wire. The framing and the cipher are the relay's, unchanged, which is what
     * lets the player decrypt this with no idea which transport it came over.
     *
     * **What this defends against and what it does not.** A passive listener on the network learns
     * nothing, which is the threat a shared Wi-Fi actually presents. An attacker able to intercept
     * and rewrite the request as it goes could substitute their own public key — the grant proves
     * the requester is authorised, but nothing yet binds it to that key. Closing that means carrying
     * the peer's identity key through Agro's grant, which is the next step and is not this one.
     */
    private fun writeEncrypted(
        output: OutputStream,
        source: java.io.InputStream,
        mime: String,
        recipientPublicKeyB64: String,
        sessionId: String
    ) {
        val roomKey = AudioStreamKeys.newRoomKey()
        val sealed = identityKeyManager.sealNote(
            recipientPublicKeyB64,
            AudioStreamKeys.encodeRoomKey(roomKey)
        )
        // No Content-Length: framing makes the byte count differ from the file's, and an encrypted
        // stream is not seekable anyway.
        output.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\n$SEALED_KEY_HEADER: $sealed\r\n" +
                "Connection: close\r\n\r\n").toByteArray()
        )
        RelayStreamFraming.encrypt(
            source,
            output,
            AudioStreamCipher(AudioStreamKeys.derive(roomKey, sessionId))
        )
        output.flush()
    }

    /** The unencrypted path, kept only for a peer running a build that cannot decrypt. */
    private fun writePlain(
        output: OutputStream,
        source: java.io.InputStream,
        mime: String,
        totalLength: Long
    ) {
        val lengthHeader = if (totalLength > 0L) "Content-Length: $totalLength\r\n" else ""
        output.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nAccept-Ranges: bytes\r\n" +
                "${lengthHeader}Connection: close\r\n\r\n").toByteArray()
        )
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (source.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private companion object {
        const val TAG = "P2PServer"

        /** The requester's X25519 identity public key, base64. Its presence turns encryption on. */
        const val IDENTITY_HEADER = "X-Wanda-Identity"

        /** Matches what `RelayDecryptingDataSource` looks for; see the note there on the name. */
        const val SEALED_KEY_HEADER = "x-agro-sealed-key"
    }
}
