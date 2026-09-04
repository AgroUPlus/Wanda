package com.wander.android.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgroVaultTest {

    @Test
    fun newVaultKeyGeneratesValidDistinctKeys() {
        val key1 = AgroVault.newVaultKey()
        val key2 = AgroVault.newVaultKey()

        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun newSaltGeneratesValidDistinctSalts() {
        val salt1 = AgroVault.newSalt()
        val salt2 = AgroVault.newSalt()

        assertEquals(16, salt1.size)
        assertEquals(16, salt2.size)
        assertFalse(salt1.contentEquals(salt2))
    }

    @Test
    fun deriveWrappingKeyIsDeterministic() {
        val passphrase = "correct horse battery staple"
        val salt = AgroVault.newSalt()

        val kek1 = AgroVault.deriveWrappingKey(passphrase, salt)
        val kek2 = AgroVault.deriveWrappingKey(passphrase, salt)

        assertEquals(32, kek1.size)
        assertArrayEquals(kek1, kek2)
    }

    @Test
    fun wrapAndUnwrapRoundTrip() {
        val passphrase = "orbit-velvet-falcon-chill"
        val salt = AgroVault.newSalt()
        val kek = AgroVault.deriveWrappingKey(passphrase, salt)

        val vaultKey = AgroVault.newVaultKey()
        val wrapped = AgroVault.wrapKey(vaultKey, kek)

        assertNotNull(wrapped)
        assertTrue(wrapped.isNotBlank())

        val recovered = AgroVault.unwrapKey(wrapped, kek)
        assertArrayEquals(vaultKey, recovered)
    }

    @Test
    fun unwrapWithWrongPassphraseFailsClosed() {
        val salt = AgroVault.newSalt()
        val correctKek = AgroVault.deriveWrappingKey("passphrase-alpha", salt)
        val wrongKek = AgroVault.deriveWrappingKey("passphrase-beta", salt)

        val vaultKey = AgroVault.newVaultKey()
        val wrapped = AgroVault.wrapKey(vaultKey, correctKek)

        assertThrows(AgroVault.VaultException::class.java) {
            AgroVault.unwrapKey(wrapped, wrongKek)
        }
    }

    @Test
    fun sealAndOpenSettingsRoundTrip() {
        val vaultKey = AgroVault.newVaultKey()
        val settingsJson = """{"server_url":"https://music.example.com","server_username":"alice"}"""

        val sealed = AgroVault.sealSettings(settingsJson, vaultKey)
        assertNotNull(sealed)
        assertFalse(sealed.contains("music.example.com"))

        val opened = AgroVault.openSettings(sealed, vaultKey)
        assertEquals(settingsJson, opened)
    }

    @Test
    fun openSettingsWithWrongKeyFailsClosed() {
        val key1 = AgroVault.newVaultKey()
        val key2 = AgroVault.newVaultKey()
        val settingsJson = """{"server_url":"https://music.example.com","server_username":"alice"}"""

        val sealed = AgroVault.sealSettings(settingsJson, key1)

        assertThrows(AgroVault.VaultException::class.java) {
            AgroVault.openSettings(sealed, key2)
        }
    }

    @Test
    fun openSettingsWithCorruptedCiphertextFailsClosed() {
        val key = AgroVault.newVaultKey()
        val settingsJson = """{"server_url":"https://music.example.com","server_username":"alice"}"""

        val sealed = AgroVault.sealSettings(settingsJson, key)
        val raw = AgroVault.decodeBase64(sealed)
        // Flip a byte in ciphertext/tag
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0xFF).toByte()
        val corrupted = AgroVault.encodeBase64(raw)

        assertThrows(AgroVault.VaultException::class.java) {
            AgroVault.openSettings(corrupted, key)
        }
    }

    @Test
    fun deriveSubkeyIsDeterministicAndDistinctForDifferentContexts() {
        val rootKey = AgroVault.newVaultKey()

        val settingsKey = AgroVault.getSettingsKey(rootKey)
        val presenceKey = AgroVault.getPresenceKey(rootKey)
        val relayKey = AgroVault.getP2pRelayKey(rootKey)

        assertEquals(32, settingsKey.size)
        assertEquals(32, presenceKey.size)
        assertEquals(32, relayKey.size)

        // All derived subkeys must be distinct from root key and each other
        assertFalse(settingsKey.contentEquals(rootKey))
        assertFalse(settingsKey.contentEquals(presenceKey))
        assertFalse(settingsKey.contentEquals(relayKey))
        assertFalse(presenceKey.contentEquals(relayKey))

        // Deterministic on same root key and context
        val settingsKey2 = AgroVault.getSettingsKey(rootKey)
        assertArrayEquals(settingsKey, settingsKey2)
    }

    @Test
    fun sealAndOpenPayloadRoundTrip() {
        val rootKey = AgroVault.newVaultKey()
        val presenceKey = AgroVault.getPresenceKey(rootKey)
        val payload = """{"trackUri":"content://media/1","title":"Windowlicker","artist":"Aphex Twin"}"""

        val sealed = AgroVault.sealPayload(payload.toByteArray(Charsets.UTF_8), presenceKey)
        assertNotNull(sealed)
        assertFalse(sealed.contains("Aphex Twin"))

        val openedBytes = AgroVault.openPayload(sealed, presenceKey, "presence")
        val opened = String(openedBytes, Charsets.UTF_8)
        assertEquals(payload, opened)

        // Wrong subkey fails
        val relayKey = AgroVault.getP2pRelayKey(rootKey)
        assertThrows(AgroVault.VaultException::class.java) {
            AgroVault.openPayload(sealed, relayKey, "presence")
        }
    }
}
