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
            if (!response.status.isSuccess()) {
                throw IOException("Agro refused the request (HTTP ${response.status.value})")
            }
            val json = HttpClientFactory.jsonConfig
                .parseToJsonElement(response.bodyAsText()).jsonObject
            (json["errors"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { errors ->
                val message = errors.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                throw IOException("Agro rejected the request: ${message ?: "unspecified GraphQL error"}")
            }
            json["data"]?.jsonObject ?: throw IOException("Agro returned no data")
        }
    }

    /**
     * The live-update socket for the paired server. `https` hosts get `wss`, so a server behind a
     * reverse proxy needs no separate configuration.
     */
    fun syncSocketUrl(): String? {
        val server = secureStorage.agroServerUrl.takeIf { it.isNotBlank() } ?: return null
        return when {
            server.startsWith("https://") -> server.replaceFirst("https://", "wss://")
            server.startsWith("http://") -> server.replaceFirst("http://", "ws://")
            else -> return null
        } + "/ws/sync"
    }
}
