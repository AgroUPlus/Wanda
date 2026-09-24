package com.wander.android.data.sources.agro

import com.wander.android.core.security.AgroVault
import com.wander.android.core.security.SecureStorage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles device pairing flows via QR code, passphrase, or device token, and initializes vault encryption keys.
 */
@Singleton
class AgroPairingCoordinator @Inject constructor(
    private val login: AgroLogin,
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage,
    private val nodeRegistrar: AgroNodeRegistrar
) {
    /**
     * `agro://connect?username=…&token=…&server=…`, as minted by the server's pairing QR.
     *
     * The QR used to carry `passphrase=`, and the app stored it as its bearer token because the
     * two were the same string. They are not any more: each scan mints its own revocable device
     * token, so photographing a code no longer hands over the account. `passphrase=` is still read
     * as a fallback and exchanged through [pairWithPassphrase] — an old QR is not a working
     * credential, but it is a thing a user can reasonably still be holding.
     */
    suspend fun parseQrCodePayload(qrString: String): Result<String?> {
        val trimmed = qrString.trim()
        if (!trimmed.startsWith("agro://connect")) {
            return Result.failure(IOException("Invalid QR: expected agro://connect"))
        }
        val uri = runCatching { android.net.Uri.parse(trimmed) }.getOrNull()
            ?: return Result.failure(IOException("Invalid URI format in QR"))
        val user = uri.getQueryParameter("username").orEmpty()
        val server = uri.getQueryParameter("server")?.takeIf { it.isNotBlank() }
            ?: secureStorage.agroServerUrl.takeIf { it.isNotBlank() }
            ?: AgroClient.DEFAULT_SERVER_URL

        val vaultKeyParam = uri.getQueryParameter("vaultKey") ?: uri.getQueryParameter("vault_key")
        if (!vaultKeyParam.isNullOrBlank()) {
            val decodedKey = runCatching {
                if (vaultKeyParam.length == 64 && vaultKeyParam.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                    hexToBytes(vaultKeyParam)
                } else {
                    AgroVault.decodeBase64(vaultKeyParam)
                }
            }.getOrNull()
            if (decodedKey != null && decodedKey.size == 32) {
                secureStorage.agroVaultKey = decodedKey
            }
        }

        uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }?.let { token ->
            return pairWithToken(server, user, token)
        }
        val legacy = uri.getQueryParameter("passphrase") ?: uri.getQueryParameter("key").orEmpty()
        if (legacy.isBlank()) {
            return Result.failure(IOException("QR code contains neither a token nor a passphrase"))
        }
        return pairWithPassphrase(server, user, legacy)
    }

    /**
     * Exchanges a passphrase for a device token, unwrap or enrols the account vault key, then pairs.
     *
     * This is what the manual Settings entry does. The passphrase is never stored: it buys a
     * credential scoped to this device — revocable on its own, without changing the passphrase
     * every other device is using — derives the vault wrapping key, and then it is discarded.
     *
     * Also accepts a direct device token or `agro://` pairing URI in the passphrase field.
     */
    suspend fun pairWithPassphrase(
        serverUrl: String,
        username: String,
        passphrase: String
    ): Result<String?> {
        val server = normalizeServerUrl(serverUrl)
            ?: return Result.failure(IOException("Server URL is invalid"))
        val trimmedUser = username.trim()
        val trimmedPass = passphrase.trim()

        if (trimmedPass.startsWith("agro://connect")) {
            return parseQrCodePayload(trimmedPass)
        }

        // If the user entered or pasted a bare device token (length >= 32, no spaces)
        if (trimmedPass.length >= 32 && !trimmedPass.contains(' ') && trimmedPass.all { it.isLetterOrDigit() || it in "-_+=/" }) {
            return pairWithToken(server, trimmedUser, trimmedPass)
        }

        if (trimmedUser.isBlank() || trimmedPass.isBlank()) {
            return Result.failure(IOException("Server, username and passphrase are all required"))
        }
        return login.exchange(server, trimmedUser, trimmedPass).fold(
            onSuccess = { loginResult ->
                val pairResult = pairWithToken(server, trimmedUser, loginResult.token)
                if (pairResult.isSuccess) {
                    setupVaultKey(trimmedUser, trimmedPass, loginResult.vaultSalt, loginResult.vaultKeyWrapped)
                }
                pairResult
            },
            onFailure = { Result.failure(it) }
        )
    }

    private suspend fun setupVaultKey(
        username: String,
        passphrase: String,
        vaultSalt: String?,
        vaultKeyWrapped: String?
    ) {
        if (!vaultSalt.isNullOrBlank() && !vaultKeyWrapped.isNullOrBlank()) {
            runCatching {
                val salt = hexToBytes(vaultSalt)
                val kek = AgroVault.deriveWrappingKey(passphrase, salt)
                val vaultKey = AgroVault.unwrapKey(vaultKeyWrapped, kek)
                secureStorage.agroVaultKey = vaultKey
            }
        } else {
            runCatching {
                val vaultKey = AgroVault.newVaultKey()
                val salt = AgroVault.newSalt()
                val kek = AgroVault.deriveWrappingKey(passphrase, salt)
                val wrapped = AgroVault.wrapKey(vaultKey, kek)
                val saltHex = bytesToHex(salt)
                val mutation = """
                    mutation EnrolVaultKey(${'$'}userId: String!, ${'$'}vaultSalt: String!, ${'$'}vaultKeyWrapped: String!) {
                        enrolVaultKey(userId: ${'$'}userId, vaultSalt: ${'$'}vaultSalt, vaultKeyWrapped: ${'$'}vaultKeyWrapped)
                    }
                """.trimIndent()
                val variables = buildJsonObject {
                    put("userId", username)
                    put("vaultSalt", saltHex)
                    put("vaultKeyWrapped", wrapped)
                }
                graphQl.execute(mutation, variables)
                secureStorage.agroVaultKey = vaultKey
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    /**
     * Stores a device token and proves it by registering.
     *
     * Credentials are rolled back when registration fails: a stored-but-unusable server made the
     * app look paired while nothing it sent could ever arrive.
     */
    suspend fun pairWithToken(
        serverUrl: String,
        username: String,
        token: String
    ): Result<String?> {
        val server = normalizeServerUrl(serverUrl)
        if (server == null || username.isBlank() || token.isBlank()) {
            return Result.failure(IOException("Server, username and a token are all required"))
        }
        // Nothing worth keeping is only true when a *complete* pairing was already stored. A
        // half-written one — a server with no username, which no longer counts as configured — is
        // not a state to fall back to, so it is cleared like a first attempt.
        val hadPairing = secureStorage.agroServerUrl.isNotBlank() &&
            secureStorage.agroUsername.isNotBlank()
        // The device id is no longer derived here: `SecureStorage.agroDeviceId` generates one on
        // first use and keeps it. Deriving it from `Build.MODEL` gave two of the same phone the
        // same identity, which a per-device library index cannot survive.
        secureStorage.setAgroCredentials(server, username, token)
        return nodeRegistrar.registerNode().onFailure { if (!hadPairing) secureStorage.clearAgroCredentials() }
    }

    /**
     * A bare hostname (`agro.example.com`, as it comes off a reverse proxy) is the common case, so
     * it gets `https://` rather than being rejected. An explicit scheme is always respected, which
     * is what keeps a plain-HTTP host on the LAN working.
     */
    fun normalizeServerUrl(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = runCatching { android.net.Uri.parse(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.scheme != "http" && uri.scheme != "https") return null
        return withScheme
    }
}
