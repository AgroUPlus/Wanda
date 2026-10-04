package com.wander.android.core.backup

import android.os.Build
import com.wander.android.BuildConfig
import com.wander.android.core.security.AgroVault
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.agro.AgroVaultApi
import com.wander.android.data.sources.agro.VaultBackup
import com.wander.android.data.sources.agro.VaultAccess
import com.wander.android.data.sources.agro.VaultLabel
import com.wander.android.data.sources.agro.VaultSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backing this device up to the Agro vault, and restoring from it.
 *
 * The same document, manifest and restore as the file export ([SettingsBackupStore]) — only the
 * envelope differs: compressed, sealed under the vault key rather than a typed passphrase, and
 * kept on Agro rather than in a file. See [CloudBackupCodec].
 *
 * Sign-ins travel only when [SecureStorage.cloudBackupAccounts] is on. Off, a restore on a new
 * phone brings everything back except them, and the sources are signed into again — which is the
 * point: the passphrase is then all that stands between a copy of the server's database and them.
 */
@Singleton
internal class CloudBackupStore @Inject constructor(
    private val contents: BackupContentsIO,
    private val api: AgroVaultApi,
    private val secureStorage: SecureStorage
) {
    val isAvailable: Boolean get() = api.isAvailable
    val access: VaultAccess get() = api.access

    /** What a backup made now carries. */
    fun sections(): Set<BackupSection> = BackupSection.entries
        .filter { it != BackupSection.ACCOUNTS || secureStorage.cloudBackupAccounts.value }
        .toSet()

    /**
     * Seals what this device holds and sends it, answering the new backup's id.
     *
     * Opened again before it is sent, as the file export reads its file back: a backup is only worth
     * anything on the day it is restored, and one that cannot be opened is found here, not then.
     */
    suspend fun backUpNow(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val document = contents.collect(sections())
            val json = BackupJson.encodeToString(BackupDocument.serializer(), document).toByteArray(Charsets.UTF_8)
            val sealed = withBackupKey { key ->
                CloudBackupCodec.pack(json, key).also { packed ->
                    val reopened = BackupJson.decodeFromString(
                        BackupDocument.serializer(),
                        String(CloudBackupCodec.unpack(packed, key), Charsets.UTF_8)
                    )
                    if (reopened.manifest != document.manifest) {
                        throw IOException("The backup did not open again as it was written")
                    }
                }
            }
            sealed to VaultLabel(
                deviceId = secureStorage.agroDeviceId,
                deviceName = secureStorage.agroDevicePetname.ifBlank { Build.MODEL },
                appVersion = BuildConfig.VERSION_NAME,
                format = CloudBackupCodec.FORMAT,
                plainBytes = json.size.toLong(),
                sections = document.manifest.map { (name, digest) -> VaultSection(name, digest.count) }
            )
        }.mapCatching { (sealed, label) -> api.upload(sealed, label).getOrThrow() }
    }

    suspend fun list(): Result<List<VaultBackup>> = api.list()

    suspend fun delete(id: String): Result<Unit> = api.delete(id)

    /**
     * Restores backup [id] over what this device holds, once every section in it has checked out.
     *
     * @throws AgroVault.VaultException when it was sealed under another vault key — a backup from
     *   before the account's key was reset cannot be opened by anyone.
     * @throws IOException when it cannot be fetched, or is damaged.
     */
    suspend fun restore(id: String): BackupContents = withContext(Dispatchers.IO) {
        val packed = api.download(id).getOrElse { throw it as? IOException ?: IOException(it.message, it) }
        val json = withBackupKey { CloudBackupCodec.unpack(packed, it) }
        val document = runCatching { BackupJson.decodeFromString(BackupDocument.serializer(), String(json, Charsets.UTF_8)) }
            .getOrElse { throw IOException("The backup's contents are damaged", it) }
        document.verifyIntegrity(BackupJson)
        contents.restore(document)
    }

    /** Runs [block] with the backup subkey, wiped as soon as it is done. */
    private fun <T> withBackupKey(block: (ByteArray) -> T): T {
        val vaultKey = secureStorage.agroVaultKey
            ?: throw IOException("This device holds no vault key — pair it with your passphrase first")
        val key = AgroVault.getBackupKey(vaultKey)
        return try {
            block(key)
        } finally {
            AgroVault.wipe(key)
        }
    }
}
