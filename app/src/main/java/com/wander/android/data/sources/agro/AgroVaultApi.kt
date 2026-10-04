package com.wander.android.data.sources.agro

import com.wander.android.core.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** One section of a backup and how many records it held — the label's whole description of it. */
@Serializable
data class VaultSection(val name: String, val count: Int)

/** What this device writes on a backup's envelope. Nothing in it is a setting, a title or a name. */
@Serializable
data class VaultLabel(
    val deviceId: String,
    val deviceName: String?,
    val appVersion: String?,
    val format: Int,
    val plainBytes: Long,
    val sections: List<VaultSection>
)

/** A backup kept on Agro, as its label describes it. */
data class VaultBackup(
    val id: String,
    val createdAt: String,
    val deviceName: String?,
    val appVersion: String?,
    val plainBytes: Long,
    val sealedBytes: Long,
    val sections: List<VaultSection>,
    val includesAccounts: Boolean,
    val sha256: String
)

/**
 * The cloud vault on the paired Agro: sending a sealed backup, listing them, fetching one back.
 *
 * Only ever handles sealed bytes. Sealing and opening are [com.wander.android.core.backup]'s, and
 * nothing passing through here could be read by the server or by anyone watching the wire.
 */
@Singleton
class AgroVaultApi @Inject constructor(
    private val graphQl: AgroGraphQl,
    private val secureStorage: SecureStorage,
    okHttpClient: OkHttpClient
) {
    /** A backup is a few megabytes at most, but a slow uplink should not trip the shared timeout. */
    private val client = okHttpClient.newBuilder()
        .writeTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
        .readTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
        .build()

    /** The server has the vault, and this device holds the key that seals for it. */
    val isAvailable: Boolean
        get() = graphQl.isConfigured && graphQl.serverSupports(CAPABILITY) && secureStorage.agroVaultKey != null

    private val base: String get() = secureStorage.agroServerUrl.trimEnd('/')

    suspend fun upload(sealed: ByteArray, label: VaultLabel): Result<String> = withContext(Dispatchers.IO) {
        val labelHex = Json.encodeToString(VaultLabel.serializer(), label).toByteArray().toHex()
        rest(
            Request.Builder()
                .url("$base/api/v1/vault/backups")
                .header(LABEL_HEADER, labelHex)
                .put(sealed.toRequestBody(OCTET))
        ) { body ->
            (REPLY_JSON.parseToJsonElement(String(body)) as? JsonObject)?.str("id")
                ?: throw IOException("the server did not say where it kept the backup")
        }
    }

    /** The sealed bytes of [id], checked against the digest the server stored with them. */
    suspend fun download(id: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        rest(Request.Builder().url("$base/api/v1/vault/backups/$id").get()) { body -> body }
    }

    suspend fun list(): Result<List<VaultBackup>> = graphQl.execute(
        """query { vaultBackups {
            id createdAt deviceName appVersion plainBytes sealedBytes includesAccounts sha256
            sections { name count }
        } }""".trimIndent(),
        buildJsonObject { }
    ).mapCatching { data ->
        data.objects("vaultBackups").map { b ->
            VaultBackup(
                id = b.str("id") ?: throw IOException("A backup arrived without an id"),
                createdAt = b.str("createdAt").orEmpty(),
                deviceName = b.str("deviceName"),
                appVersion = b.str("appVersion"),
                plainBytes = b.long("plainBytes"),
                sealedBytes = b.long("sealedBytes"),
                sections = b.objects("sections").map { VaultSection(it.str("name").orEmpty(), it.long("count").toInt()) },
                includesAccounts = b.bool("includesAccounts"),
                sha256 = b.str("sha256").orEmpty()
            )
        }
    }

    suspend fun delete(id: String): Result<Unit> = graphQl.execute(
        "mutation(\$id: String!) { deleteVaultBackup(id: \$id) }",
        buildJsonObject { put("id", id) }
    ).map { }

    /** One authenticated byte request, through the same refused-token gate as every Agro call. */
    private fun <T> rest(builder: Request.Builder, read: (ByteArray) -> T): Result<T> {
        val token = secureStorage.agroApiKey
        AgroTokenGate.refusing(token)?.let { return Result.failure(it) }
        return runCatching {
            client.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { response ->
                val body = response.body.bytes()
                if (!response.isSuccessful) {
                    AgroTokenGate.recordRestStatus(token, response.code)
                    throw IOException("the server refused the backup (HTTP ${response.code})")
                }
                response.header(SHA256_HEADER)?.let { expected ->
                    if (!body.sha256().equals(expected, ignoreCase = true)) {
                        throw IOException("the backup arrived damaged")
                    }
                }
                read(body)
            }
        }
    }

    companion object {
        const val CAPABILITY = "vault.backups"
        private const val LABEL_HEADER = "X-Agro-Vault-Label"
        private const val SHA256_HEADER = "X-Agro-Vault-Sha256"
        private val OCTET = "application/octet-stream".toMediaType()
        private val REPLY_JSON = Json { ignoreUnknownKeys = true }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
        private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()
    }
}
