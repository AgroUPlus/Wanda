package com.wander.android.data.sources.agro

import com.wander.android.core.network.HttpClientFactory
import com.wander.android.core.security.SecureStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single way anything talks to an Agro server: one POST to `/graphql` carrying the paired
 * credentials. Split out from [AgroClient] so the write side (registration, handoff) and the read
 * side ([AgroSessionApi]) share exactly one definition of what a failure is.
 */
@Singleton
class AgroGraphQl @Inject constructor(
    private val client: HttpClient,
    private val secureStorage: SecureStorage
) {
    val isConfigured: Boolean get() = secureStorage.agroConfigured.value

    val userId: String get() = secureStorage.agroUsername

    val deviceId: String get() = secureStorage.agroDeviceId

    /** For the WebSocket, which authenticates with the same token as `/graphql`. */
    val apiKey: String get() = secureStorage.agroApiKey

    /**
     * GraphQL answers a rejected or malformed mutation with HTTP 200 and an `errors` array, so a
     * status check alone reported every such failure as a successful sync. Both cases fail here,
     * carrying the server's own message — without the URL or key, which are credentials.
     */
    suspend fun execute(
        query: String,
        variables: JsonObject
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IOException("Agro is not paired"))
        }
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        runCatching {
            val response = client.post("${secureStorage.agroServerUrl}/graphql") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${secureStorage.agroApiKey}")
                setBody(body.toString())
            }
            val text = response.bodyAsText()
            val json = runCatching {
                HttpClientFactory.jsonConfig.parseToJsonElement(text).jsonObject
            }.getOrNull()
            val firstError = (json?.get("errors") as? JsonArray)?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull

            if (!response.status.isSuccess()) {
                throw AgroAuthError.of(response.status.value, firstError)
            }
            if (json == null) {
                throw IOException("Agro did not answer with JSON")
            }
            if (firstError != null) {
                throw authErrorOrNull(firstError)
                    ?: IOException("Agro rejected the request: $firstError")
            }
            json["data"]?.jsonObject ?: throw IOException("Agro returned no data")
        }
    }

    /**
     * Recognises the two rejections that mean the stored credential is no longer good.
     *
     * The middleware answers 401/403 for a token it cannot resolve, but a resolver that refuses the
     * caller answers HTTP 200 with `Forbidden` in the errors array. Both mean the same thing to the
     * app — stop trusting what is in storage — and only the typed error lets callers tell that
     * apart from an ordinary query mistake.
     */
    private fun authErrorOrNull(message: String): AgroAuthError? = when {
        message.contains("Unauthorized", ignoreCase = true) -> AgroAuthError.Rejected(message)
        message.contains("not active", ignoreCase = true) -> AgroAuthError.NotActive(message)
        message.contains("Forbidden", ignoreCase = true) -> AgroAuthError.Rejected(message)
        else -> null
    }

    /**
     * The live-update socket for the paired server. `https` hosts get `wss`, so a server behind a
     * reverse proxy needs no separate configuration.
     */
    fun syncSocketUrl(): String? {
        val server = secureStorage.agroServerUrl.takeIf { it.isNotBlank() } ?: return null
        val base = when {
            server.startsWith("https://") -> server.replaceFirst("https://", "wss://")
            server.startsWith("http://") -> server.replaceFirst("http://", "ws://")
            else -> return null
        } + "/ws/sync"
        val deviceId = secureStorage.agroDeviceId
        if (deviceId.isBlank()) return base

        // The LAN address rides the handshake because the server holds it only in memory, for as
        // long as this socket is open. `registerNode` sends it too, but that runs once at app
        // start, so on its own an address cleared by a redeploy or a dropped socket never came
        // back and peer transfers silently fell back to the relay. Reconnecting restores it now,
        // which this client already does with backoff.
        //
        // Re-read on every connection: a phone changes network often, and the address it had when
        // the process started is the wrong one by the time it reconnects elsewhere.
        val lan = LocalNetwork.lanAddress()
        val encodedDevice = java.net.URLEncoder.encode(deviceId, "UTF-8")
        return if (lan != null) {
            "$base?device=$encodedDevice&lan=" + java.net.URLEncoder.encode(lan, "UTF-8")
        } else {
            "$base?device=$encodedDevice"
        }
    }
}
