package com.wander.android.core.sync

import android.content.Context
import android.net.Uri
import com.wander.android.core.database.dao.TrackDao
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
    private val secureStorage: SecureStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isRunning = false

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

            if (method == "GET" && path.startsWith("/p2p/fetch/")) {
                val hash = path.removePrefix("/p2p/fetch/").substringBefore("?")
                val track = trackDao.findByContentHash(hash)
                if (track != null && track.streamUri != null) {
                    try {
                        val uri = Uri.parse(track.streamUri)
                        val fileStream = context.contentResolver.openInputStream(uri)
                        if (fileStream != null) {
                            fileStream.use { fileIn ->
                                val available = fileIn.available().toLong()
                                val lengthHeader = if (available > 0) "Content-Length: $available\r\n" else ""
                                val header = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n${lengthHeader}Connection: close\r\n\r\n"
                                output.write(header.toByteArray())

                                val streamBuf = ByteArray(64 * 1024)
                                var read: Int
                                while (fileIn.read(streamBuf).also { read = it } != -1) {
                                    output.write(streamBuf, 0, read)
                                }
                                output.flush()
                                return
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed streaming track $hash", e)
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

    private companion object {
        const val TAG = "P2PServer"
    }
}
