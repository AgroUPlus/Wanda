package com.wander.android.core.backup

import android.content.Context
import android.net.Uri
import com.wander.android.core.security.AgroVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes and reads the backup file — the same [BackupCodec] format as the Agro vault, locked with a
 * passphrase instead of the vault key.
 *
 * ## Why it is encrypted, and not optional
 *
 * The backup carries the contents of `SecureStorage`, which is where this app keeps the Navidrome
 * password, the YouTube session cookie and the Agro device token. Those live in
 * `EncryptedSharedPreferences` precisely so they are not readable at rest, and a plaintext export
 * would undo that with one tap — the file lands in Downloads, gets synced to a cloud drive, and the
 * account it protects is in it. So the passphrase is not a setting: there is no unencrypted path.
 *
 * Argon2id is deliberately slow (~64 MiB, three passes), so both calls are off the main thread.
 */
@Singleton
internal class SettingsBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contents: BackupContentsIO
) {

    /**
     * Seals the chosen [sections] into [target], then reads the file back and opens it.
     *
     * The read-back is the insurance: a backup is only worth anything on the day it is restored,
     * and a file that was truncated by the storage provider or cannot be decrypted is discovered
     * here — while the data is still on this device — rather than on that day.
     *
     * @throws IOException if the document cannot be written, or the written file does not verify.
     */
    suspend fun export(
        target: Uri,
        passphrase: String,
        sections: Set<BackupSection>
    ): BackupContents = withContext(Dispatchers.IO) {
        val document = contents.collect(sections)
        val sealed = BackupCodec.sealWithPassphrase(document.toJsonBytes(), passphrase)
        context.contentResolver.openOutputStream(target, "wt")
            ?.use { it.write(sealed) }
            ?: throw IOException("Could not open the backup file for writing")

        val written = try {
            read(target, passphrase)
        } catch (e: AgroVault.VaultException) {
            throw IOException("The written backup could not be opened again — keep your old one", e)
        }
        if (written.manifest != document.manifest) {
            throw IOException("The written backup does not match this device — keep your old one")
        }
        document.summary()
    }

    /**
     * Restores a backup over what this device holds — but only once every section in it has been
     * checked against its manifest. A damaged file restores nothing, rather than part of itself.
     *
     * @throws IOException if the file cannot be read, is not a Wanda backup, or fails verification.
     * @throws AgroVault.VaultException if the passphrase is wrong or the file was altered.
     */
    suspend fun import(source: Uri, passphrase: String): BackupContents = withContext(Dispatchers.IO) {
        contents.restore(read(source, passphrase))
    }

    /** Opens, decrypts, decodes and verifies — everything short of writing. */
    private fun read(source: Uri, passphrase: String): BackupDocument {
        val packed = context.contentResolver.openInputStream(source)
            ?.use { it.readBytes() }
            ?: throw IOException("Could not open the backup file")
        return BackupCodec.openWithPassphrase(packed, passphrase).toVerifiedDocument()
    }
}

/** One configuration for writing, hashing and reading, so a digest means the same on each side. */
internal val BackupJson = Json { ignoreUnknownKeys = true }

/** What a backup holds, or what a restore put back, so the user can be told something true. */
internal data class BackupContents(
    val settings: Int,
    val plays: Int,
    val recaps: Int,
    val tracks: Int = 0,
    val playlists: Int = 0
)

private fun BackupDocument.summary() = BackupContents(
    settings = entries.size + accounts.size,
    plays = history.size,
    recaps = recaps.size,
    tracks = tracks.size,
    playlists = playlists.size
)
