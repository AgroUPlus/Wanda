package com.wander.android.core.backup

import com.wander.android.core.security.AgroVault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** A backup opens as it was written, with the key it was locked with and nothing else. */
class BackupCodecTest {

    private val vaultKey = AgroVault.newVaultKey()
    private val key = AgroVault.getBackupKey(vaultKey)

    /** Listening history is this repetitive, which is why compressing first pays. */
    private val json = buildString {
        append("""{"entries":{},"history":[""")
        repeat(2000) { append("""{"title":"Song $it","artist":"The Band","playedAt":"2026-10-04T20:00:00Z"},""") }
        append("""{}]}""")
    }.toByteArray()

    @Test
    fun aVaultBackupOpensExactlyAsItWasWritten() {
        assertArrayEquals(json, BackupCodec.openWithVaultKey(BackupCodec.sealWithVaultKey(json, key), key))
    }

    @Test
    fun aPassphraseBackupOpensExactlyAsItWasWritten() {
        val packed = BackupCodec.sealWithPassphrase(json, "correct horse")
        assertArrayEquals(json, BackupCodec.openWithPassphrase(packed, "correct horse"))
    }

    @Test
    fun bothAreCompressedBeforeTheyAreSealed() {
        val vault = BackupCodec.sealWithVaultKey(json, key)
        val file = BackupCodec.sealWithPassphrase(json, "correct horse")
        assertTrue("${vault.size} of ${json.size}", vault.size < json.size / 5)
        assertTrue("${file.size} of ${json.size}", file.size < json.size / 5)
    }

    @Test
    fun theHeaderSaysWhatLocksIt() {
        assertEquals(BackupLock.VAULT, BackupCodec.lockOf(BackupCodec.sealWithVaultKey(json, key)))
        assertEquals(BackupLock.PASSPHRASE, BackupCodec.lockOf(BackupCodec.sealWithPassphrase(json, "x")))
    }

    @Test
    fun aWrongPassphraseOrAnotherVaultCannotOpenIt() {
        val file = BackupCodec.sealWithPassphrase(json, "correct horse")
        assertThrows(AgroVault.VaultException::class.java) { BackupCodec.openWithPassphrase(file, "wrong horse") }

        val vault = BackupCodec.sealWithVaultKey(json, key)
        val stranger = AgroVault.getBackupKey(AgroVault.newVaultKey())
        assertThrows(AgroVault.VaultException::class.java) { BackupCodec.openWithVaultKey(vault, stranger) }
    }

    @Test
    fun theBackupKeyIsNotTheSettingsKey() {
        val packed = BackupCodec.sealWithVaultKey(json, key)
        assertThrows(AgroVault.VaultException::class.java) {
            BackupCodec.openWithVaultKey(packed, AgroVault.getSettingsKey(vaultKey))
        }
    }

    @Test
    fun openingWithTheWrongKindOfKeySaysWhereItBelongs() {
        val vault = BackupCodec.sealWithVaultKey(json, key)
        val error = assertThrows(IOException::class.java) { BackupCodec.openWithPassphrase(vault, "x") }
        assertTrue(error.message!!.contains("Agro"))
    }

    @Test
    fun bytesThatAreNotABackupOrAnotherFormatAreRefusedByName() {
        assertThrows(IOException::class.java) { BackupCodec.lockOf("not a backup".toByteArray()) }
        val other = BackupCodec.sealWithVaultKey(json, key).also { it[4] = 9 }
        val error = assertThrows(IOException::class.java) { BackupCodec.openWithVaultKey(other, key) }
        assertTrue(error.message!!.contains("another version"))
    }

    @Test
    fun anAlteredBackupDoesNotOpen() {
        val packed = BackupCodec.sealWithVaultKey(json, key).also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertThrows(AgroVault.VaultException::class.java) { BackupCodec.openWithVaultKey(packed, key) }
    }
}
