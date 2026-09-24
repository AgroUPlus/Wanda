package com.wander.android.data.sources.agro

import com.wander.android.core.security.IdentityKeyManager
import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles device registration, public key publication, presence announcement, and identity verification.
 */
@Singleton
class AgroNodeRegistrar @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage,
    private val identityKeyManager: IdentityKeyManager
) {
    /**
     * Battery-first one-shot registration: called on app launch or pairing only.
     * Never runs in an unconstrained background loop.
     */
    suspend fun registerNode(currentTrack: String? = null): Result<String?> {
        val lanAddress = LocalNetwork.lanAddress()

        // Publish E2EE identity public key
        runCatching {
            val pubKeyB64 = identityKeyManager.getPublicKeyBase64()
            val keyMutation = """
                mutation SetPublicKey(${'$'}publicKey: String, ${'$'}deviceId: String) {
                    setPublicKey(publicKey: ${'$'}publicKey, deviceId: ${'$'}deviceId) { publicKey }
                }
            """.trimIndent()
            // Under this device's own id. Without it every sign-in published over the last one,
            // and the phone that was already paired stopped being able to read its own messages.
            graphQl.execute(
                keyMutation,
                buildJsonObject {
                    put("publicKey", pubKeyB64)
                    put("deviceId", secureStorage.agroDeviceId)
                }
            )
        }

        val variables = buildJsonObject {
            put("userId", secureStorage.agroUsername)
            put("deviceId", secureStorage.agroDeviceId)
            put("clientType", "wanda")
            secureStorage.agroDevicePetname.ifEmpty { null }?.let { put("deviceName", it) }
            lanAddress?.let { put("lanAddress", it) }
            currentTrack?.let { put("currentTrack", it) }
        }

        // Asks what the server can do at the same time as announcing this device. A server too old
        // to have the field rejects the whole query, so the answer is remembered and the plain form
        // retried — once, here, rather than by every later request discovering it for itself.
        val withCapabilities = graphQl.execute(registerNodeMutation(askCapabilities = true), variables)
        val result = if (withCapabilities.isSuccess ||
            !AgroGraphQl.isUnknownFieldError(withCapabilities, "capabilities")
        ) {
            withCapabilities
        } else {
            secureStorage.agroCapabilities = emptySet()
            graphQl.execute(registerNodeMutation(askCapabilities = false), variables)
        }

        return result.map { data ->
            val node = data["registerNode"]?.jsonObject
            val petname = node?.get("petname")?.jsonPrimitive?.contentOrNull
            if (!petname.isNullOrBlank()) secureStorage.setAgroDevicePetname(petname)
            (node?.get("capabilities") as? JsonArray)?.let { advertised ->
                secureStorage.agroCapabilities = advertised
                    .mapNotNull { it.jsonPrimitive.contentOrNull }
                    .toSet()
            }
            petname
        }
    }

    private fun registerNodeMutation(askCapabilities: Boolean): String {
        val fields = if (askCapabilities) "petname capabilities" else "petname"
        return """
            mutation RegisterNode(${'$'}userId: String!, ${'$'}deviceId: String!, ${'$'}clientType: String!, ${'$'}deviceName: String, ${'$'}lanAddress: String, ${'$'}currentTrack: String) {
                registerNode(userId: ${'$'}userId, deviceId: ${'$'}deviceId, clientType: ${'$'}clientType, deviceName: ${'$'}deviceName, lanAddress: ${'$'}lanAddress, currentTrack: ${'$'}currentTrack) {
                    $fields
                }
            }
        """.trimIndent()
    }

    /**
     * Asks the server who this device's stored token belongs to.
     *
     * The only way to find out that a credential has stopped working. A revoked app password or a
     * suspended account produces no event on the device — every subsequent query simply fails, and
     * with nothing checking, Settings went on reporting a healthy pairing indefinitely. `me` is the
     * cheapest field that proves the whole chain: the token resolves, the account is active, and it
     * is the account whose name we have stored.
     */
    internal suspend fun verify(): Result<AgroIdentity> {
        val query = """
            query Me(${'$'}username: String!) {
                me(username: ${'$'}username) { username role state }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("username", secureStorage.agroUsername) }

        return graphQl.execute(query, variables).mapCatching { data ->
            val me = data["me"]?.jsonObject
                ?: throw AgroAuthError.Rejected("This server has no account by that name")
            val state = me["state"]?.jsonPrimitive?.contentOrNull
            if (state != null && !state.equals("active", ignoreCase = true)) {
                throw AgroAuthError.NotActive("This account is $state")
            }
            AgroIdentity(
                username = me["username"]?.jsonPrimitive?.contentOrNull
                    ?: secureStorage.agroUsername,
                role = me["role"]?.jsonPrimitive?.contentOrNull ?: "member"
            )
        }
    }

    /** Unregisters this device from Agro on unpair or credential reset. */
    suspend fun unregisterNode(): Result<Unit> {
        val mutation = """
            mutation UnregisterNode(${'$'}userId: String!, ${'$'}deviceId: String!) {
                unregisterNode(userId: ${'$'}userId, deviceId: ${'$'}deviceId)
            }
        """.trimIndent()
        val variables = buildJsonObject {
            put("userId", secureStorage.agroUsername)
            put("deviceId", secureStorage.agroDeviceId)
        }
        return graphQl.execute(mutation, variables).discardPayload()
    }
}
