package com.wander.android.data.sources.agro

import com.wander.android.core.network.HttpClientFactory
import com.wander.android.core.security.SecureStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a fresh signup got, shown exactly once.
 *
 * The server hashes the passphrase the moment it answers and can never show it again, so this is
 * the only chance the user has to write it down. [isPending] decides what we say next: an account
 * waiting on the admin cannot log in yet, and saying so is the difference between "wait" and
 * "something is broken".
 */
internal data class AgroSignup(
    val username: String,
    val passphrase: String,
    val isPending: Boolean
)

/**
 * The device token and optional vault envelope returned by `/api/v1/login`.
 */
internal data class AgroLoginResult(
    val token: String,
    val vaultSalt: String?,
    val vaultKeyWrapped: String?
)

/**
 * The endpoints that can be reached without an existing bearer token: signing up, and trading an
 * account passphrase for a device token.
 *
 * The passphrase is never stored locally. It buys a credential scoped to this device — revocable on
 * its own, without changing what every other device uses — and is then discarded.
 */
@Singleton
class AgroLogin @Inject constructor(
    private val client: HttpClient,
    private val secureStorage: SecureStorage
) {
    internal suspend fun exchange(
        serverUrl: String,
        username: String,
        passphrase: String
    ): Result<AgroLoginResult> = post(
        url = "$serverUrl/api/v1/login",
        body = buildJsonObject {
            put("username", username.trim())
            put("passphrase", passphrase)
            put("label", secureStorage.agroDevicePetname.ifEmpty { "Wanda Android" })
        }
    ).mapCatching { json ->
        val token = json["token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw AgroAuthError.Server(
                json["error"]?.jsonPrimitive?.contentOrNull ?: "Server returned no device token"
            )
        val vaultSalt = json["vaultSalt"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val vaultKeyWrapped = json["vaultKeyWrapped"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        AgroLoginResult(
            token = token,
            vaultSalt = vaultSalt,
            vaultKeyWrapped = vaultKeyWrapped
        )
    }

    /**
     * Creates an account on a server that accepts strangers.
     *
     * Whether the account comes back usable or queued for approval is the server's decision, not
     * ours — an instance can be open, invite-only, or closed — so the answer is reported rather
     * than assumed.
     */
    internal suspend fun signup(
        serverUrl: String,
        username: String,
        inviteCode: String? = null
    ): Result<AgroSignup> = post(
        url = "$serverUrl/api/v1/signup",
        body = buildJsonObject {
            put("username", username.trim())
            inviteCode?.takeIf { it.isNotBlank() }?.let { put("invite_code", it.trim()) }
            put("label", secureStorage.agroDevicePetname.ifEmpty { "Wanda Android" })
        }
    ).mapCatching { json ->
        val passphrase = json["passphrase"]?.jsonPrimitive?.contentOrNull
        if (passphrase.isNullOrBlank()) {
            throw AgroAuthError.Server(
                json["error"]?.jsonPrimitive?.contentOrNull ?: "Server returned no passphrase"
            )
        }
        AgroSignup(
            username = json["username"]?.jsonPrimitive?.contentOrNull ?: username.trim(),
            passphrase = passphrase,
            isPending = json["state"]?.jsonPrimitive?.contentOrNull
                ?.equals("pending", ignoreCase = true) ?: false
        )
    }

    /**
     * One unauthenticated POST, with every failure already turned into an [AgroAuthError].
     *
     * Both endpoints answer the same shape — a JSON object, with `error` carrying the reason on a
     * non-2xx — so the status mapping belongs here rather than in each caller.
     */
    private suspend fun post(url: String, body: JsonObject): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
                val json = runCatching {
                    HttpClientFactory.jsonConfig.parseToJsonElement(response.bodyAsText()).jsonObject
                }.getOrNull()

                if (!response.status.isSuccess()) {
                    throw AgroAuthError.of(
                        status = response.status.value,
                        serverMessage = json?.get("error")?.jsonPrimitive?.contentOrNull
                    )
                }
                json ?: throw AgroAuthError.Server("The server did not answer with JSON")
            }.recoverCatching { throw AgroAuthError.from(it) }
        }
}
