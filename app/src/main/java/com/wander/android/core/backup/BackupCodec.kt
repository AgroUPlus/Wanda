package com.wander.android.core.backup

import com.wander.android.core.security.AgroVault
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** What opens a backup: the account's vault key (Agro) or a passphrase typed at export (a file). */
internal enum class BackupLock { VAULT, PASSPHRASE }

/**
 * Every Wanda backup, in a file or in the Agro vault: `WVLT`, a format byte, a [BackupLock] byte,
 * the 16-byte salt when a passphrase locks it, then the gzipped [BackupDocument] JSON sealed with
 * AES-256-GCM.
 *
 * One format for both, so they share one reader and one set of checks; only the key differs.
 * Gzipped *before* sealing, because sealed bytes look like noise and do not compress — listening
 * history is repetitive JSON and shrinks to a fraction of its size.
 *
 * ## The keys
 *
 * A vault-locked backup uses [AgroVault.getBackupKey] of the account's vault key, which every
 * paired device holds, so a backup made on one phone opens on the next with nothing to type. A
 * passphrase-locked one uses Argon2id over the passphrase and a fresh salt, which is not secret
 * and is stored in the clear so the passphrase can be checked at all. Agro holds neither key.
 *
 * GCM authenticates, so a wrong key and an altered backup fail the same way: as a failure, never
 * as an empty backup that restores nothing and looks like success.
 */
internal object BackupCodec {

    private val MAGIC = byteArrayOf('W'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    const val FORMAT = 2
    private const val HEADER_BYTES = 6
    private const val SALT_BYTES = 16

    fun lockOf(packed: ByteArray): BackupLock {
        if (packed.size <= HEADER_BYTES || !packed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IOException("That is not a Wanda backup")
        }
        if (packed[4].toInt() != FORMAT) {
            throw IOException("That backup was made by another version of Wanda, which this one cannot read")
        }
        return BackupLock.entries.getOrNull(packed[5].toInt()) ?: throw IOException("That is not a Wanda backup")
    }

    fun sealWithVaultKey(json: ByteArray, backupKey: ByteArray): ByteArray =
        header(BackupLock.VAULT) + AgroVault.sealBytes(gzip(json), backupKey)

    fun sealWithPassphrase(json: ByteArray, passphrase: String): ByteArray {
        val salt = AgroVault.newSalt()
        check(salt.size == SALT_BYTES)
        val key = AgroVault.deriveWrappingKey(passphrase, salt)
        return try {
            header(BackupLock.PASSPHRASE) + salt + AgroVault.sealBytes(gzip(json), key)
        } finally {
            AgroVault.wipe(key)
        }
    }

    /**
     * @throws IOException if [packed] is not a vault-locked backup this version reads.
     * @throws AgroVault.VaultException if another account's key sealed it, or it was altered.
     */
    fun openWithVaultKey(packed: ByteArray, backupKey: ByteArray): ByteArray {
        val body = bodyLockedBy(packed, BackupLock.VAULT)
        return gunzip(AgroVault.openBytes(body, backupKey, "backup"))
    }

    /**
     * @throws IOException if [packed] is not a passphrase-locked backup this version reads.
     * @throws AgroVault.VaultException if the passphrase is wrong, or the backup was altered.
     */
    fun openWithPassphrase(packed: ByteArray, passphrase: String): ByteArray {
        val body = bodyLockedBy(packed, BackupLock.PASSPHRASE)
        if (body.size <= SALT_BYTES) throw IOException("The backup's header is damaged")
        val key = AgroVault.deriveWrappingKey(passphrase, body.copyOfRange(0, SALT_BYTES))
        return try {
            gunzip(AgroVault.openBytes(body.copyOfRange(SALT_BYTES, body.size), key, "backup"))
        } finally {
            AgroVault.wipe(key)
        }
    }

    private fun bodyLockedBy(packed: ByteArray, expected: BackupLock): ByteArray {
        when (lockOf(packed)) {
            expected -> Unit
            BackupLock.VAULT -> throw IOException("That backup is locked with your vault key — restore it from Agro")
            BackupLock.PASSPHRASE -> throw IOException("That backup is locked with a passphrase — import it as a file")
        }
        return packed.copyOfRange(HEADER_BYTES, packed.size)
    }

    private fun header(lock: BackupLock) = MAGIC + byteArrayOf(FORMAT.toByte(), lock.ordinal.toByte())

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}

internal fun BackupDocument.toJsonBytes(): ByteArray =
    BackupJson.encodeToString(BackupDocument.serializer(), this).toByteArray(Charsets.UTF_8)

/** Decodes what a backup opened to, and refuses it unless every section checks out. */
internal fun ByteArray.toVerifiedDocument(): BackupDocument {
    val document = runCatching { BackupJson.decodeFromString(BackupDocument.serializer(), String(this, Charsets.UTF_8)) }
        .getOrElse { throw IOException("The backup's contents are damaged", it) }
    document.verifyIntegrity(BackupJson)
    return document
}
