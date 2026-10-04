package com.wander.android.core.backup

import com.wander.android.core.security.AgroVault
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** A cloud backup opens as it was written, for the vault it was sealed for and nobody else. */
class CloudBackupCodecTest {

    private val vaultKey = AgroVault.newVaultKey()
    private val key = AgroVault.getBackupKey(vaultKey)

    /** Listening history is this repetitive, which is why compressing first pays. */
    private val json = buildString {
        append("""{"version":3,"entries":{},"history":[""")
        repeat(2000) { append("""{"title":"Song $it","artist":"The Band","playedAt":"2026-10-04T20:00:00Z"},""") }
        append("""{}]}""")
    }.toByteArray()

    @Test
    fun aBackupOpensExactlyAsItWasWritten() {
        assertArrayEquals(json, CloudBackupCodec.unpack(CloudBackupCodec.pack(json, key), key))
    }

    @Test
    fun itIsCompressedBeforeItIsSealed() {
        val packed = CloudBackupCodec.pack(json, key)
        assertTrue("${packed.size} of ${json.size}", packed.size < json.size / 5)
    }

    @Test
    fun anotherVaultsKeyCannotOpenIt() {
        val packed = CloudBackupCodec.pack(json, key)
        val stranger = AgroVault.getBackupKey(AgroVault.newVaultKey())
        assertThrows(AgroVault.VaultException::class.java) { CloudBackupCodec.unpack(packed, stranger) }
    }

    @Test
    fun theBackupKeyIsNotTheSettingsKey() {
        val packed = CloudBackupCodec.pack(json, key)
        assertThrows(AgroVault.VaultException::class.java) {
            CloudBackupCodec.unpack(packed, AgroVault.getSettingsKey(vaultKey))
        }
    }

    @Test
    fun bytesThatAreNotABackupOrAreFromANewerFormatAreRefusedByName() {
        assertThrows(IOException::class.java) { CloudBackupCodec.unpack("not a backup".toByteArray(), key) }
        val newer = CloudBackupCodec.pack(json, key).also { it[4] = 9 }
        val error = assertThrows(IOException::class.java) { CloudBackupCodec.unpack(newer, key) }
        assertTrue(error.message!!.contains("newer"))
    }

    @Test
    fun anAlteredBackupDoesNotOpen() {
        val packed = CloudBackupCodec.pack(json, key).also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertThrows(AgroVault.VaultException::class.java) { CloudBackupCodec.unpack(packed, key) }
    }
}
