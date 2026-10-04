package com.wander.android.core.backup

import com.wander.android.core.security.AgroVault
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * A cloud backup on the wire: `WVLT`, a format byte, an encoding byte, then the sealed payload.
 *
 * ## Compressed, unlike the file export
 *
 * The local file is plain JSON under its seal so an older build can still open a newer one (see
 * [SettingsBackupStore.export]). A cloud backup has no older readers — it is new — so it carries
 * its encoding in the header from the first version, and is gzipped *before* sealing: sealed bytes
 * are indistinguishable from noise and do not compress. Listening history is repetitive JSON, and
 * typically shrinks to a fraction of its size.
 *
 * ## The key
 *
 * [AgroVault.getBackupKey] of the account's vault key — not a passphrase. Every device paired to
 * the account holds that key, and a new one gets it by typing the passphrase once at pairing, so a
 * backup made on one phone opens on the next without a second secret to remember. The server holds
 * neither the key nor anything it can be derived from.
 */
internal object CloudBackupCodec {

    private val MAGIC = byteArrayOf('W'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    const val FORMAT = 1
    private const val ENCODING_GZIP: Byte = 1
    private const val HEADER_BYTES = 6

    /** Compresses and seals [json] under [backupKey]. */
    fun pack(json: ByteArray, backupKey: ByteArray): ByteArray {
        val compressed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json) } }.toByteArray()
        return MAGIC + byteArrayOf(FORMAT.toByte(), ENCODING_GZIP) + AgroVault.sealBytes(compressed, backupKey)
    }

    /**
     * Opens what [pack] made.
     *
     * @throws IOException if the bytes are not a cloud backup, or are one from a newer format.
     * @throws AgroVault.VaultException if they were sealed under another account's key or altered.
     */
    fun unpack(packed: ByteArray, backupKey: ByteArray): ByteArray {
        if (packed.size <= HEADER_BYTES || !packed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IOException("That is not a Wanda cloud backup")
        }
        if (packed[4].toInt() != FORMAT || packed[5] != ENCODING_GZIP) {
            throw IOException("That backup was made by a newer Wanda — update to restore it")
        }
        val compressed = AgroVault.openBytes(packed.copyOfRange(HEADER_BYTES, packed.size), backupKey, "cloud backup")
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
    }
}
