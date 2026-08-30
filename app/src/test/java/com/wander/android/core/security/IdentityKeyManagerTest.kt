package com.wander.android.core.security

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

class IdentityKeyManagerTest {

    private class FakeSecureStorage : SecureStorageHolder {
        override var agroIdentityPrivateKey: String? = null
        override var agroIdentityPublicKey: String? = null
    }

    interface SecureStorageHolder {
        var agroIdentityPrivateKey: String?
        var agroIdentityPublicKey: String?
    }

    @Test
    fun sealAndOpenNoteRoundTrip() {
        val random = SecureRandom()
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(random))

        val keyPair = generator.generateKeyPair()
        val priv = keyPair.private as X25519PrivateKeyParameters
        val pub = keyPair.public as X25519PublicKeyParameters
        val pubB64 = Base64.getEncoder().encodeToString(pub.encoded)

        // Ephemeral SealedBox helper test
        val secretMessage = "Hey! Check out this confidential track preview \uD83C\uDFB5"

        // Encrypt with ephemeral key targeting recipient public key
        val ephemeralKeyPair = generator.generateKeyPair()
        val ephemeralPriv = ephemeralKeyPair.private as X25519PrivateKeyParameters
        val ephemeralPub = ephemeralKeyPair.public as X25519PublicKeyParameters

        val agreement = org.bouncycastle.crypto.agreement.X25519Agreement()
        agreement.init(ephemeralPriv)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(pub, sharedSecret, 0)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val symKey = digest.digest(sharedSecret)

        val nonce = ByteArray(12)
        random.nextBytes(nonce)

        val cipher = org.bouncycastle.crypto.modes.ChaCha20Poly1305()
        cipher.init(true, org.bouncycastle.crypto.params.AEADParameters(org.bouncycastle.crypto.params.KeyParameter(symKey), 128, nonce))

        val plainBytes = secretMessage.toByteArray(Charsets.UTF_8)
        val cipherBytes = ByteArray(cipher.getOutputSize(plainBytes.size))
        val len = cipher.processBytes(plainBytes, 0, plainBytes.size, cipherBytes, 0)
        cipher.doFinal(cipherBytes, len)

        val payload = ByteArray(32 + 12 + cipherBytes.size)
        System.arraycopy(ephemeralPub.encoded, 0, payload, 0, 32)
        System.arraycopy(nonce, 0, payload, 32, 12)
        System.arraycopy(cipherBytes, 0, payload, 44, cipherBytes.size)

        val ciphertextB64 = Base64.getEncoder().encodeToString(payload)
        assertNotEquals(secretMessage, ciphertextB64)

        // Recipient Decryption
        val decPayload = Base64.getDecoder().decode(ciphertextB64)
        val rxEphemeralPub = ByteArray(32)
        val rxNonce = ByteArray(12)
        val rxCipher = ByteArray(decPayload.size - 32 - 12)
        System.arraycopy(decPayload, 0, rxEphemeralPub, 0, 32)
        System.arraycopy(decPayload, 32, rxNonce, 0, 12)
        System.arraycopy(decPayload, 44, rxCipher, 0, rxCipher.size)

        val rxAgreement = org.bouncycastle.crypto.agreement.X25519Agreement()
        rxAgreement.init(priv)
        val rxSharedSecret = ByteArray(rxAgreement.agreementSize)
        rxAgreement.calculateAgreement(X25519PublicKeyParameters(rxEphemeralPub, 0), rxSharedSecret, 0)

        val rxSymKey = java.security.MessageDigest.getInstance("SHA-256").digest(rxSharedSecret)
        val rxCipherMode = org.bouncycastle.crypto.modes.ChaCha20Poly1305()
        rxCipherMode.init(false, org.bouncycastle.crypto.params.AEADParameters(org.bouncycastle.crypto.params.KeyParameter(rxSymKey), 128, rxNonce))

        val rxPlain = ByteArray(rxCipherMode.getOutputSize(rxCipher.size))
        val decLen = rxCipherMode.processBytes(rxCipher, 0, rxCipher.size, rxPlain, 0)
        val finalLen = rxCipherMode.doFinal(rxPlain, decLen)

        val decrypted = String(rxPlain, 0, decLen + finalLen, Charsets.UTF_8)
        assertEquals(secretMessage, decrypted)
    }
}
