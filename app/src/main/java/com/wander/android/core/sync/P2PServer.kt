package com.wander.android.core.sync

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import com.wander.android.core.database.dao.TrackDao
import com.wander.android.core.p2p.OffGridNowPlaying
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Embedded lightweight HTTP server on Android port 8702.
 * Serves audio files directly over the local Wi-Fi network to peer devices.
 */
@Singleton
class P2PServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val secureStorage: SecureStorage,
    private val identityKeyManager: IdentityKeyManager,
    private val playerConnection: Provider<PlayerConnection>
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "P2P request failed: ${error.javaClass.simpleName}")
        }
    )
    private var serverSocket: ServerSocket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isRunning = false

    private val activeTransfers = AtomicInteger(0)
    private val grantManager = P2PGrantManager(identityKeyManager)
    private val audioWriter = P2PAudioResponseWriter(identityKeyManager)

    internal val pairedPeers: StateFlow<List<PairedPeer>> = grantManager.pairedPeers

    fun clearPairingGrants() = grantManager.clearPairingGrants()
    internal fun revokePairing(deviceId: Int) = grantManager.revokePairing(deviceId)
    fun acceptGrant(token: String, forUser: String, forKeys: List<String>, ttlSeconds: Long) =
        grantManager.acceptGrant(token, forUser, forKeys, ttlSeconds)

    private suspend fun nowPlaying(): OffGridNowPlaying {
        val connection = playerConnection.get()
        val state = connection.state.value
        val track = state.currentTrack ?: return OffGridNowPlaying.IDLE

        val positionMs = withContext(Dispatchers.Main) {
            runCatching { connection.controller.value?.currentPosition }.getOrNull() ?: 0L
        }.coerceAtLeast(0L)

        val hash = runCatching { trackDao.getTrackById(track.id)?.contentHash }.getOrNull()
        return OffGridNowPlaying(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            contentHash = hash,
            positionMs = positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying
        )
    }

    suspend fun start(port: Int = 8702): Result<Unit> {
        if (isRunning) return Result.success(Unit)

        val server = withContext(Dispatchers.IO) {
            runCatching { ServerSocket(port, 50, InetAddress.getByName("0.0.0.0")) }
        }.getOrElse { error ->
            Log.e(TAG, "Failed to start P2PServer", error)
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

    private fun acquireWifiLockForTransfer() {
        if (activeTransfers.getAndIncrement() != 0) return
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "wanda:p2p")
                ?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WifiLock", e)
        }
    }

    private fun releaseWifiLockAfterTransfer() {
        if (activeTransfers.decrementAndGet() > 0) return
        releaseWifiLock()
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.release()
        } catch (e: Exception) {
            Log.d(TAG, "Wi-Fi lock was already released", e)
        }
        wifiLock = null
    }

    fun stop() {
        isRunning = false
        activeTransfers.set(0)
        releaseWifiLock()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.d(TAG, "Server socket was already closed", e)
        }
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        acquireWifiLockForTransfer()
        try {
            serve(socket)
        } catch (e: Exception) {
            Log.i(TAG, "peer went away mid-request: ${e.javaClass.simpleName}")
        } finally {
            releaseWifiLockAfterTransfer()
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
            Log.d(TAG, "Handling P2P request: $method ${path.substringBefore('?')}")

            if (method == "GET" && path == "/p2p/ping") {
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nConnection: close\r\n\r\npong".toByteArray())
                output.flush()
                return
            }

            if (method == "GET" && path.startsWith("/p2p/pair")) {
                val requesterKey = Uri.parse("http://localhost$path").getQueryParameter("key").orEmpty()
                val sealed = grantManager.mintPairingGrant(requesterKey)
                val response = if (sealed == null) {
                    "HTTP/1.1 400 Bad Request\r\nContent-Length: 11\r\nConnection: close\r\n\r\nBad Request"
                } else {
                    val body = identityKeyManager.getPublicKeyBase64() + "\n" + sealed
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                }
                output.write(response.toByteArray())
                output.flush()
                return
            }

            if (method == "GET" && path.startsWith("/p2p/now-playing")) {
                if (!grantManager.isAuthorised(request)) {
                    audioWriter.writeForbidden(output)
                    return
                }
                val body = Json.encodeToString(OffGridNowPlaying.serializer(), nowPlaying())
                val bytes = body.toByteArray()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(bytes)
                output.flush()
                return
            }

            if (method == "GET" && path.startsWith("/p2p/unpair")) {
                val requesterKey = Uri.parse("http://localhost$path").getQueryParameter("key").orEmpty()
                grantManager.unpair(requesterKey)
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                output.flush()
                return
            }

            if (method == "GET" && (path.startsWith(FETCH_PREFIX) || path.startsWith("/p2p/stream"))) {
                if (!grantManager.isAuthorised(request)) {
                    audioWriter.writeForbidden(output)
                    return
                }

                val fetchHash = if (path.startsWith(FETCH_PREFIX)) path.removePrefix(FETCH_PREFIX).substringBefore("?") else null
                val queryUri = Uri.parse("http://localhost$path")
                val queryHash = queryUri.getQueryParameter("hash") ?: fetchHash
                val requestedTrack = queryUri.getQueryParameter("track")

                val track = when {
                    queryHash != null -> trackDao.findByContentHash(queryHash)
                    requestedTrack != null -> {
                        val playing = nowPlaying().trackId
                        if (playing != null && playing == requestedTrack) trackDao.getTrackById(requestedTrack) else null
                    }
                    else -> null
                }

                if (audioWriter.streamTrack(context, track, request, queryUri, grantManager, output)) {
                    return
                }

                output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 9\r\nConnection: close\r\n\r\nNot Found".toByteArray())
                output.flush()
                return
            }

            output.write("HTTP/1.1 400 Bad Request\r\nContent-Length: 11\r\nConnection: close\r\n\r\nBad Request".toByteArray())
            output.flush()
        }
    }

    private companion object {
        const val TAG = "P2PServer"
        const val FETCH_PREFIX = "/p2p/fetch/"
    }
}
