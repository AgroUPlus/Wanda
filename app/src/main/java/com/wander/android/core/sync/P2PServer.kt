package com.wander.android.core.sync

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.asStateFlow
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
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException
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
    private val identityKeyManager: IdentityKeyManager,
    /**
     * Read only to answer `/p2p/now-playing`, and through a [javax.inject.Provider] so that this
     * server does not have to be constructed after the player. The dependency is one-directional
     * and shallow — a title, an artist and a position — but a direct injection would tie the
     * lifetime of the thing that *serves* audio to the thing that *plays* it, and off-grid those
     * are deliberately separate: a phone can serve a track it is not itself listening to.
     */
    private val playerConnection:
        javax.inject.Provider<com.wander.android.core.playback.PlayerConnection>
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

    private val _pairedPeers =
        kotlinx.coroutines.flow.MutableStateFlow<List<PairedPeer>>(emptyList())

    /**
     * The peers that have paired *with this device*, face to face.
     *
     * Being connected used to be knowable only by the phone that did the connecting. The device
     * that was tapped raised a group, minted a grant and started serving audio without anything on
     * its screen saying so — and it could not tell the difference between a peer that had gone and
     * one that had never come, because nothing recorded either.
     *
     * Only the pairing grants are reflected here. An Agro-issued grant is not a face-to-face link
     * and has no business appearing on the off-grid screen.
     */
    internal val pairedPeers: kotlinx.coroutines.flow.StateFlow<List<PairedPeer>> =
        _pairedPeers.asStateFlow()

    /**
     * A peer that paired with this device.
     *
     * Named by its identity key, because off-grid that is the only name either device has — there
     * are no accounts and no server that could supply one. [fingerprint] is the same eight bytes
     * the beacon advertises, so a screen can match this against the row the user tapped.
     */
    internal data class PairedPeer(
        val publicKeyB64: String,
        val fingerprint: ByteArray,
        val pairedAtMs: Long
    ) {
        // A data class with a ByteArray needs these written out; the generated ones compare by
        // identity, which would make two readings of the same peer look like different peers.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            val that = other as? PairedPeer ?: return false
            return publicKeyB64 == that.publicKeyB64 && pairedAtMs == that.pairedAtMs
        }

        override fun hashCode(): Int = 31 * publicKeyB64.hashCode() + pairedAtMs.hashCode()
    }

    private data class Grant(
        val forUser: String,
        /**
         * The identity keys this grant may be sealed to, or empty when it names none.
         *
         * Empty is the pre-binding behaviour and is kept deliberately: an off-grid pairing grant
         * carries the peer's key in [forUser] instead, and an older Agro sends no key list at all.
         * Refusing to serve in either case would break working setups to close a gap neither of
         * them has.
         */
        val boundKeys: List<String>,
        val expiresAtMs: Long,
        /**
         * Which arrangement issued it, and therefore which teardown may revoke it.
         *
         * Off-grid disconnect used to clear the whole map, so unpairing from a phone in a car
         * also revoked the grants Agro had issued for an unrelated listen-along over the house
         * Wi-Fi — the listener kept a token this device had silently stopped honouring, and every
         * request afterwards was answered `403`.
         */
        val origin: Origin
    )

    private enum class Origin {
        /** Minted by Agro and pushed over the sync socket. Lives and dies with that session. */
        AGRO,

        /** Minted here, face to face, for a peer with no path to Agro. Dies with the link. */
        PAIRING
    }

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
        record(
            token,
            requesterPublicKeyB64,
            // Face to face there is no Agro to name the keys, but the requester's key *is* the
            // identity this grant was minted for, so it binds itself.
            listOf(requesterPublicKeyB64),
            PAIR_GRANT_TTL_SECONDS,
            Origin.PAIRING
        )
        rememberPairedPeer(requesterPublicKeyB64)
        return sealed
    }

    /**
     * Records a grant pushed by Agro as `P2P_GRANT`.
     *
     * Re-announcing an existing token is expected rather than exceptional: this map is memory-only,
     * so the process dying takes every grant with it while the server keeps handing the listener
     * the same one. An upsert is what makes that recoverable without a new token.
     */
    fun acceptGrant(token: String, forUser: String, forKeys: List<String>, ttlSeconds: Long) =
        record(token, forUser, forKeys, ttlSeconds, Origin.AGRO)

    private fun record(
        token: String,
        forUser: String,
        forKeys: List<String>,
        ttlSeconds: Long,
        origin: Origin
    ) {
        if (token.isBlank()) return
        grants.entries.removeAll { it.value.expiresAtMs <= System.currentTimeMillis() }
        grants[token] = Grant(
            forUser = forUser,
            boundKeys = forKeys.filter { it.isNotBlank() },
            expiresAtMs = System.currentTimeMillis() + ttlSeconds * 1000L,
            origin = origin
        )
    }

    /**
     * The key a stream for this request may be sealed to, or null to refuse.
     *
     * The decision itself is [GrantBinding.sealingKey], which is pure and tested; this only finds
     * the grant the token names.
     */
    private fun sealingKeyFor(token: String, headerKey: String?): String? {
        val grant = grants[token] ?: return null
        return GrantBinding.sealingKey(grant.boundKeys, headerKey)
    }

    /** The bearer token on a request, or empty. Shared by the authorisation and sealing paths. */
    private fun tokenOf(request: String): String = request.lineSequence()
        .firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.removePrefix("Bearer ")
        ?.trim()
        .orEmpty()

    /**
     * Drops the grants minted face to face, leaving Agro's alone.
     *
     * Called when a peer link is torn down. Only the pairing grants depended on that link; an
     * Agro-issued grant belongs to a session over an entirely different interface and revoking it
     * here was the bug — stopping an off-grid share silently broke LAN streaming until the app was
     * restarted.
     */
    fun clearPairingGrants() {
        grants.entries.removeAll { it.value.origin == Origin.PAIRING }
        _pairedPeers.value = emptyList()
    }

    /**
     * Forgets one peer and the grant it was given.
     *
     * What `/p2p/unpair` calls, so that a peer which has stopped sharing stops appearing here —
     * and stops being able to fetch audio — the moment it says so, rather than when its grant
     * happens to run out ten minutes later.
     */
    private fun unpair(publicKeyB64: String) {
        if (publicKeyB64.isBlank()) return
        grants.entries.removeAll {
            it.value.origin == Origin.PAIRING && it.value.forUser == publicKeyB64
        }
        _pairedPeers.value = _pairedPeers.value.filterNot { it.publicKeyB64 == publicKeyB64 }
    }

    /**
     * Stops honouring the pairing granted to the peer with this beacon device id.
     *
     * Keyed by the beacon id rather than the key because that is what the screen has: the row the
     * user is looking at names a device, not a base64 blob. The id is derived from the key, so the
     * lookup is a recomputation rather than a second source of truth.
     */
    internal fun revokePairing(deviceId: Int) {
        val peer = _pairedPeers.value.firstOrNull {
            com.wander.android.core.p2p.OffGridBeacon.deviceIdFrom(
                runCatching {
                    android.util.Base64.decode(it.publicKeyB64, android.util.Base64.NO_WRAP)
                }.getOrDefault(ByteArray(0))
            ) == deviceId
        } ?: return
        unpair(peer.publicKeyB64)
    }

    private fun rememberPairedPeer(publicKeyB64: String) {
        val fingerprint = runCatching {
            com.wander.android.core.p2p.OffGridBeacon.fingerprintFrom(
                android.util.Base64.decode(publicKeyB64, android.util.Base64.NO_WRAP)
            )
        }.getOrNull() ?: return
        val peer = PairedPeer(publicKeyB64, fingerprint, System.currentTimeMillis())
        // Re-pairing replaces rather than appends: the same device asking again is the same link,
        // and a list that grew on every retry would report a crowd where there is one phone.
        _pairedPeers.value =
            _pairedPeers.value.filterNot { it.publicKeyB64 == publicKeyB64 } + peer
    }

    /**
     * This device's playback, as a peer needs to see it.
     *
     * The content hash is looked up here rather than carried on the track: `UnifiedTrack` has no
     * hash field, and the peer stream is addressed by hash alone — so without this lookup a
     * follower would be told a title it could then not fetch. A track with no hash is reported
     * with a null one, which is the honest form of "this one cannot be followed": it is something
     * the host is streaming, and off-grid there is no network for the listener to stream it from
     * either.
     */
    private suspend fun nowPlaying(): com.wander.android.core.p2p.OffGridNowPlaying {
        val connection = playerConnection.get()
        val state = connection.state.value
        val track = state.currentTrack ?: return com.wander.android.core.p2p.OffGridNowPlaying.IDLE

        // `PlaybackState` carries no playhead — it is sampled separately, off the controller, and
        // Media3 insists that be done on the main thread. Read here rather than approximated, since
        // the whole use of this number is to start a follower at the right second.
        val positionMs = withContext(Dispatchers.Main) {
            runCatching { connection.controller.value?.currentPosition }.getOrNull() ?: 0L
        }.coerceAtLeast(0L)

        val hash = runCatching { trackDao.getTrackById(track.id)?.contentHash }.getOrNull()
        return com.wander.android.core.p2p.OffGridNowPlaying(
            title = track.title,
            artist = track.artist,
            album = track.album,
            contentHash = hash,
            positionMs = positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying
        )
    }

    private fun writeForbidden(output: OutputStream) {
        val body = "forbidden"
        output.write(
            ("HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                .toByteArray()
        )
        output.flush()
    }

    private fun isAuthorised(request: String): Boolean {
        val token = tokenOf(request)
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

    /**
     * Binds the port and starts accepting, answering whether it actually worked.
     *
     * **Suspending, and the bind happens before it returns.** It used to launch the whole thing and
     * return immediately, so a caller could not find out whether the port was taken — the failure
     * was one line in the log, and everything downstream carried on as though audio were being
     * served. `startAdvertising(servesAudio = true)` then told the room a promise this device could
     * not keep: peers found it, tapped it, paired with it, and got nothing.
     *
     * The common way to fail is [java.net.BindException] with `EADDRINUSE`, which on a developer's
     * phone means another build of Wanda is installed and already holding 8702. That is worth
     * saying in those words rather than as "off-grid does not work".
     */
    suspend fun start(port: Int = 8702): Result<Unit> {
        if (isRunning) return Result.success(Unit)

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wanda:p2p")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WifiLock", e)
        }

        val server = withContext(Dispatchers.IO) {
            runCatching { ServerSocket(port, 50, InetAddress.getByName("0.0.0.0")) }
        }.getOrElse { error ->
            Log.e(TAG, "Failed to start P2PServer", error)
            releaseWifiLock()
            return Result.failure(bindFailure(port, error))
        }

        serverSocket = server
        isRunning = true
        Log.i(TAG, "P2PServer listening on port $port")

        scope.launch {
            while (isRunning && !server.isClosed) {
                try {
                    val client = server.accept()
                    scope.launch { handleClient(client) }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
        return Result.success(Unit)
    }

    /**
     * Why the port could not be taken, in words that name the cause.
     *
     * `EADDRINUSE` on this port has one overwhelmingly likely explanation and it is not something
     * the user can guess: a second build of Wanda — a debug build beside a release one — installed
     * and running. Both bind 8702, and the second one silently loses.
     */
    private fun bindFailure(port: Int, cause: Throwable): Throwable {
        val inUse = cause is java.net.BindException ||
            cause.message?.contains("EADDRINUSE", ignoreCase = true) == true
        val message = if (inUse) {
            "Port $port is already taken, usually by another copy of Wanda installed on this " +
                "phone. Close or uninstall the other one and try again."
        } else {
            "This phone could not open the port that serves audio: ${cause.message}"
        }
        return IOException(message, cause)
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.release()
        } catch (ignored: Exception) {
        }
        wifiLock = null
    }

    fun stop() {
        isRunning = false
        releaseWifiLock()
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

            // What this device is playing, for a peer following it with no server between them.
            //
            // Behind the grant, unlike `/p2p/ping`: what somebody is listening to is exactly the
            // kind of thing the grant exists to gate, and a device on the same Wi-Fi that has not
            // paired has no business reading it.
            if (method == "GET" && path.startsWith("/p2p/now-playing")) {
                if (!isAuthorised(request)) {
                    writeForbidden(output)
                    return
                }
                val body = kotlinx.serialization.json.Json.encodeToString(
                    com.wander.android.core.p2p.OffGridNowPlaying.serializer(),
                    nowPlaying()
                )
                val bytes = body.toByteArray()
                output.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                        .toByteArray()
                )
                output.write(bytes)
                output.flush()
                return
            }

            // Hanging up. Open like the pair it undoes, and it proves itself the same way: the
            // key names which pairing to drop, and knowing a key only ever lets you end your own
            // link. Announced rather than left to expire so that stopping a share is visible on
            // both screens at once, which is the whole complaint it answers.
            if (method == "GET" && path.startsWith("/p2p/unpair")) {
                val requesterKey = Uri.parse("http://localhost$path")
                    .getQueryParameter("key")
                    .orEmpty()
                unpair(requesterKey)
                val body = "ok"
                output.write(
                    ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" +
                        "Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                        .toByteArray()
                )
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
                            // Checked against the grant, never taken on trust. A rewritten
                            // header names a key the grant does not, and is not sealed to.
                            val recipientKey =
                                sealingKeyFor(tokenOf(request), identityKeyOf(request))
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
     * The key is sealed to a public key the *grant* names, so it is readable by that peer and by
     * nothing else on the wire. The framing and the cipher are the relay's, unchanged, which is what
     * lets the player decrypt this with no idea which transport it came over.
     *
     * **What this defends against.** A passive listener on the network learns nothing, which is the
     * threat a shared Wi-Fi actually presents. An attacker who can rewrite the request in flight is
     * also covered now: they can put their own key in `X-Wanda-Identity`, but an Agro-issued grant
     * carries the keys the listener has published, and a key that is not among them is not sealed
     * to. The bearer token proves the requester is authorised; the bound set proves they are who
     * the grant was minted for.
     *
     * **Where the header still stands alone.** Off-grid, where the grant was minted face to face
     * and carries the peer's own key as its identity — that binding is [mintPairingGrant]'s, not
     * Agro's. And against an older Agro that sends no key list, where trusting the header is what
     * the previous build did and refusing to serve would break a working setup to close a gap that
     * server has no way to help with.
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
