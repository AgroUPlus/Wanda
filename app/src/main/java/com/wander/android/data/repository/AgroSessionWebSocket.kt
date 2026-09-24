package com.wander.android.data.repository

import android.util.Log
import com.wander.android.core.network.ConnectivityObserver
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.data.sources.agro.AgroGraphQl
import com.wander.android.data.sources.agro.AgroLiveMessage
import com.wander.android.data.sources.agro.LocalNetwork
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Manages the WebSocket lifecycle, reconnection with exponential backoff, and event stream dispatch.
 */
internal class AgroSessionWebSocket(
    private val graphQl: AgroGraphQl,
    private val connectivity: ConnectivityObserver,
    private val identityKeyManager: IdentityKeyManager
) {
    @Volatile
    private var lastSeq: Long = 0L

    fun liveUpdates(): Flow<AgroLiveMessage> = connectOnce().retryWhen { _, attempt ->
        connectivity.isOnline.first { it }
        val backoff = (BASE_BACKOFF_MS shl attempt.coerceAtMost(5).toInt())
            .coerceAtMost(MAX_BACKOFF_MS)
        delay(backoff)
        true
    }

    private fun connectOnce(): Flow<AgroLiveMessage> = callbackFlow {
        val url = graphQl.syncSocketUrl().takeIf { connectivity.isOnline.value }
        if (url == null) {
            close()
            return@callbackFlow
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${graphQl.apiKey}")
            .build()

        val socket = HttpClientFactory.okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val authFrame = buildJsonObject {
                        put("msg_type", "AUTH")
                        put("payload", buildJsonObject {
                            put("token", graphQl.apiKey)
                            put("device", graphQl.deviceId)
                            put("lan", LocalNetwork.lanAddress())
                        })
                    }
                    webSocket.send(authFrame.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (AgroLiveMessageParser.isAuthSuccess(text)) {
                        if (lastSeq > 0L) webSocket.send(AgroLiveMessageParser.resumeFrame(lastSeq))
                        return
                    }
                    if (AgroLiveMessageParser.needsResync(text)) {
                        lastSeq = 0L
                        trySend(AgroLiveMessage.Resync)
                        return
                    }
                    AgroLiveMessageParser.sequenceOf(text)?.let { seq ->
                        if (seq > lastSeq) lastSeq = seq
                    }
                    AgroLiveMessageParser.parse(text, graphQl.userId, identityKeyManager)?.let {
                        trySend(it)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Agro live updates stopped: ${t.message}")
                    close(t)
                }
            }
        )
        awaitClose { socket.close(NORMAL_CLOSURE, null) }
    }

    private companion object {
        const val TAG = "AgroSessions"
        const val NORMAL_CLOSURE = 1000
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
