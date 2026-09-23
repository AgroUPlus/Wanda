package com.wander.android.core.backup

import android.content.Context
import android.net.Uri
import com.wander.android.core.security.AgroVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes and reads the backup file.
 *
 * ## Why it is encrypted, and not optional
 *
 * The backup carries the contents of `SecureStorage`, which is where this app keeps the Navidrome
 * password, the YouTube session cookie and the Agro device token. Those live in
 * `EncryptedSharedPreferences` precisely so they are not readable at rest, and a plaintext export
 * would undo that with one tap — the file lands in Downloads, gets synced to a cloud drive, and the
 * account it protects is in it. So the passphrase is not a setting: there is no unencrypted path.
 *
 * ## The scheme
 *
 * Argon2id over the passphrase, then AES-256-GCM over the payload — both borrowed whole from
 * [AgroVault] rather than reimplemented, because this is the same problem it already solves: a
 * human-typed passphrase standing between an attacker and a file they hold. The salt is fresh per
 * export and stored beside the ciphertext; it is not a secret, and without it a passphrase cannot
 * be verified at all.
 *
 * GCM authenticates, so a wrong passphrase and a damaged file fail the same way — as a failure.
 * Neither is ever reported as an empty backup, which would restore nothing and look like success.
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
        // Not gzipped, deliberately. The payload being plain JSON is what lets an older build
        // decrypt a newer file and restore the parts it understands; compression would turn that
        // graceful degradation into "the backup's contents are damaged".
        val document = contents.collect(sections)
        val salt = AgroVault.newSalt()
        val key = AgroVault.deriveWrappingKey(passphrase, salt)
        val envelope = try {
            BackupEnvelope(
                salt = Base64.getEncoder().encodeToString(salt),
                payload = AgroVault.sealPayload(
                    BackupJson.encodeToString(document).toByteArray(Charsets.UTF_8),
                    key
                )
            )
        } finally {
            // The derived key has no other holder, so this actually shortens its life.
            AgroVault.wipe(key)
        }

        context.contentResolver.openOutputStream(target, "wt")
            ?.use { it.write(BackupJson.encodeToString(envelope).toByteArray(Charsets.UTF_8)) }
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
        val raw = context.contentResolver.openInputStream(source)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Could not open the backup file")

        val envelope = runCatching { BackupJson.decodeFromString<BackupEnvelope>(raw) }
            .getOrElse { throw IOException("That file is not a Wanda backup", it) }
        if (envelope.format != BACKUP_FORMAT) {
            throw IOException("That file is not a Wanda backup")
        }

        val salt = runCatching { Base64.getDecoder().decode(envelope.salt) }
            .getOrElse { throw IOException("The backup's header is damaged", it) }

        val key = AgroVault.deriveWrappingKey(passphrase, salt)
        val plaintext = try {
            // Throws `VaultException` on a wrong passphrase — GCM cannot tell that from tampering,
            // and the caller must not treat either as an empty backup.
            AgroVault.openPayload(envelope.payload, key, "settings backup")
        } finally {
            AgroVault.wipe(key)
        }

        val document = runCatching {
            BackupJson.decodeFromString<BackupDocument>(String(plaintext, Charsets.UTF_8))
        }.getOrElse { throw IOException("The backup's contents are damaged", it) }
        document.verifyIntegrity(BackupJson)
        return document
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

/**
 * The unencrypted header, so a file can be recognised — and rejected — before a passphrase is
 * derived, which costs hundreds of milliseconds.
 *
 * Nothing here is secret: a format name, a version, and the salt, which is public by construction.
 */
@kotlinx.serialization.Serializable
private data class BackupEnvelope(
    val format: String = BACKUP_FORMAT,
    val version: Int = BackupDocument.CURRENT_VERSION,
    val salt: String,
    val payload: String
)

private const val BACKUP_FORMAT = "wanda-backup"
