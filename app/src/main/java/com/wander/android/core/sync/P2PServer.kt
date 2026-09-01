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
import kotlinx.coroutines.CoroutineExceptionHandler
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
    /**
     * Nothing served to a peer may take this process down.
     *
     * A `launch` whose exception nobody catches reaches the thread's default handler, and on
     * Android that ends the app. Serving audio is full of ordinary, unavoidable throws — the
     * commonest being a broken pipe when the listener's player closes the source, which it does on
     * every seek, stop and error — so the device *sharing* its music was being crashed by the
     * normal behaviour of the device receiving it.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "P2P request failed: ${error.javaClass.simpleName}")
        }
    )
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

    /**
     * Mints a grant for a peer that asked for one face to face, and seals it to their key.
     *
     * The off-grid tier has no Agro to issue grants: two phones in a car have never met a server
     * and may never meet one. Without this the radio link came up and the very first request for
     * audio was answered `403`, which is why tier 5 could not work no matter what the UI did.
     *
     * **Sealed, not returned in the clear.** A Wi-Fi Direct group is no more a confidentiality
     * boundary than a LAN is — the same argument [writeEncrypted] already makes about the audio.
     * Sealing to the caller's own public key means only the holder of the matching private key can
     * read the token, so overhearing the handshake buys nothing.
     *
     * **The grant is bound to that key**, which is what lets the caller verify afterwards that the
     * device it reached is the device it chose — and closes the substitution [writeEncrypted]
     * warns about, where nothing yet tied the grant to an identity.
     *
     * Null when the key is unusable. Being advertised is the consent here: [BleDiscovery] treats
     * advertising as a deliberate act with a lifetime, so a device that is findable has already
     * said yes. A per-transfer prompt, the way AirDrop asks, would be a further step.
     */
    private fun mintPairingGrant(requesterPublicKeyB64: String): String? {
        if (requesterPublicKeyB64.isBlank()) return null
        val token = ByteArray(PAIR_TOKEN_BYTES)
            .also { java.security.SecureRandom().nextBytes(it) }
            .let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        val sealed = runCatching {
            identityKeyManager.sealNote(requesterPublicKeyB64, token)
        }.getOrNull() ?: return null
        // Recorded against the requester's key rather than an account name: off-grid, there are no
        // accounts, and the key is the only identity either device has.
        acceptGrant(token, forUser = requesterPublicKeyB64, ttlSeconds = PAIR_GRANT_TTL_SECONDS)
        return sealed
    }

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
        // Every write below can throw once the peer has gone away, and most of them sit outside
        // the narrower guard around the audio path. A hung-up listener is not an error worth more
        // than a line in the log.
        try {
            serve(socket)
        } catch (e: Exception) {
            Log.i(TAG, "peer went away mid-request: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun serve(socket: Socket) {
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

            // Face-to-face pairing, open like the ping above and for the same reason: it is what
            // a peer must be able to reach *before* it holds a grant. What it hands back is sealed,
            // so being open costs nothing.
            if (method == "GET" && path.startsWith("/p2p/pair")) {
                val requesterKey = Uri.parse("http://localhost$path")
                    .getQueryParameter("key")
                    .orEmpty()
                val sealed = mintPairingGrant(requesterKey)
                val response = if (sealed == null) {
                    "HTTP/1.1 400 Bad Request\r\nContent-Length: 11\r\nConnection: close\r\n\r\nBad Request"
                } else {
                    // The server's own identity travels back with it: the caller recomputes the
                    // beacon fingerprint from it and checks it reached the device it picked.
                    val body = identityKeyManager.getPublicKeyBase64() + "\n" + sealed
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" +
                        "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n" + body
                }
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

        /** 256 bits of grant. It is a bearer token on an open port; guessing must be hopeless. */
        const val PAIR_TOKEN_BYTES = 32

        /**
         * How long a face-to-face grant lasts.
         *
         * The length of a shared listen, not of a friendship. An off-grid grant is handed to
         * whoever asked over the radio link, so it must expire on its own — there is no server to
         * revoke it and the link it was issued for may be gone long before the token would be.
         */
        const val PAIR_GRANT_TTL_SECONDS = 30L * 60L
    }
}
