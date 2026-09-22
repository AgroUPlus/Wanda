package com.wander.android.core.backup

import android.content.Context
import android.net.Uri
import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.ReplayRecapDao
import com.wander.android.core.security.AgroVault
import com.wander.android.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes and reads the settings backup file.
 *
 * ## Why it is encrypted, and not optional
 *
 * The backup is the contents of [SecureStorage], which is where this app keeps the Navidrome
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
    private val secureStorage: SecureStorage,
    private val historyDao: HistoryDao,
    private val replayRecapDao: ReplayRecapDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Seals every stored preference into [target].
     *
     * @throws IOException if the document cannot be written.
     */
    suspend fun export(
        target: Uri,
        passphrase: String,
        includeHistory: Boolean = true
    ): Unit = withContext(Dispatchers.IO) {
        val document = BackupDocument(
            entries = secureStorage.exportAll().toBackupEntries(),
            // Not gzipped, deliberately. The payload being plain JSON is what lets a build that
            // predates these two fields decrypt a file written here and restore the settings out
            // of it; compression would turn that graceful degradation into "the backup's contents
            // are damaged". A heavy listener's history is a few megabytes, which is a size this
            // path already handles.
            history = if (includeHistory) {
                historyDao.allPlays().map { BackupPlay(it.trackId, it.playedAt) }
            } else {
                emptyList()
            },
            recaps = if (includeHistory) {
                replayRecapDao.observeAll().first().map { it.toBackup() }
            } else {
                emptyList()
            }
        )
        val salt = AgroVault.newSalt()
        val key = AgroVault.deriveWrappingKey(passphrase, salt)
        val envelope = try {
            BackupEnvelope(
                salt = Base64.getEncoder().encodeToString(salt),
                payload = AgroVault.sealPayload(
                    json.encodeToString(document).toByteArray(Charsets.UTF_8),
                    key
                )
            )
        } finally {
            // The derived key has no other holder, so this actually shortens its life.
            AgroVault.wipe(key)
        }

        context.contentResolver.openOutputStream(target, "wt")
            ?.use { it.write(json.encodeToString(envelope).toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Could not open the backup file for writing")
    }

    /**
     * Restores a backup over what this device holds.
     *
     * The three sections restore differently, on purpose: preferences are *replaced*, saved recaps
     * are replaced per year, and listening history is *merged* — see [restoreHistory].
     *
     * Returns what was written, so the caller can say something true about what happened rather
     * than "done".
     *
     * @throws IOException if the file cannot be read or is not a Wanda backup.
     * @throws AgroVault.VaultException if the passphrase is wrong or the file is damaged.
     */
    suspend fun import(source: Uri, passphrase: String): BackupContents = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(source)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Could not open the backup file")

        val envelope = runCatching { json.decodeFromString<BackupEnvelope>(raw) }
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
            json.decodeFromString<BackupDocument>(String(plaintext, Charsets.UTF_8))
        }.getOrElse { throw IOException("The backup's contents are damaged", it) }

        val values = document.entries.toPreferenceValues()
        secureStorage.importAll(values)

        restoreHistory(document.history)
        // Replaced by year rather than merged: two recaps of the same year describe the same year,
        // and the restored one is at least as complete as whatever this device had.
        document.recaps.forEach { replayRecapDao.save(it.toEntity()) }

        BackupContents(
            settings = values.size,
            plays = document.history.size,
            recaps = document.recaps.size
        )
    }

    /**
     * Adds restored plays without ever removing one.
     *
     * A merge, not a replace: restoring last month's backup onto a device that has been playing
     * music since must not delete what it played in between. Already-present plays are filtered out
     * so restoring the same file twice does not double a year's listening.
     */
    private suspend fun restoreHistory(plays: List<BackupPlay>) {
        if (plays.isEmpty()) return
        val existing = historyDao.allPlays().mapTo(mutableSetOf()) { it.trackId to it.playedAt }
        val fresh = plays.filterNot { (it.trackId to it.playedAt) in existing }
        fresh.chunked(INSERT_BATCH).forEach { batch ->
            historyDao.insertPlays(batch.map { it.toEntity() })
        }
    }

    private companion object {
        /** A restore can carry tens of thousands of rows; SQLite dislikes one statement that big. */
        const val INSERT_BATCH = 500
    }
}

/** What a restore actually put back, so the user can be told something true. */
internal data class BackupContents(
    val settings: Int,
    val plays: Int,
    val recaps: Int
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
