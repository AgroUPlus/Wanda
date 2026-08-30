package com.wander.android.core.security

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages X25519 cryptographic identity keys and handles end-to-end encryption for track drops.
 *
 * Uses ephemeral-static Diffie-Hellman (X25519 SealedBox) with ChaCha20-Poly1305 authenticated
 * encryption, perfectly compatible with Wander (Rust).
 */
@Singleton
class IdentityKeyManager @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val random = SecureRandom()

    /**
     * Returns the local X25519 keypair, generating and persisting a new one in [SecureStorage]
     * if none exists.
     */
    @Synchronized
    fun getOrCreateIdentityKeys(): Pair<X25519PrivateKeyParameters, X25519PublicKeyParameters> {
        val privB64 = secureStorage.agroIdentityPrivateKey
        val pubB64 = secureStorage.agroIdentityPublicKey

        if (!privB64.isNullOrBlank() && !pubB64.isNullOrBlank()) {
            try {
                val privBytes = Base64.getDecoder().decode(privB64.trim())
                val pubBytes = Base64.getDecoder().decode(pubB64.trim())
                if (privBytes.size == 32 && pubBytes.size == 32) {
                    val privKey = X25519PrivateKeyParameters(privBytes, 0)
                    val pubKey = X25519PublicKeyParameters(pubBytes, 0)
                    return Pair(privKey, pubKey)
                }
            } catch (_: Exception) {
                // Generate a fresh keypair if stored key is corrupt
            }
        }

        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(random))
        val keyPair = generator.generateKeyPair()

        val privKey = keyPair.private as X25519PrivateKeyParameters
        val pubKey = keyPair.public as X25519PublicKeyParameters

        val privEncoded = Base64.getEncoder().encodeToString(privKey.encoded)
        val pubEncoded = Base64.getEncoder().encodeToString(pubKey.encoded)

        secureStorage.agroIdentityPrivateKey = privEncoded
        secureStorage.agroIdentityPublicKey = pubEncoded

        return Pair(privKey, pubKey)
    }

    /**
     * Gets the Base64-encoded public key for publishing to Agro.
     */
    fun getPublicKeyBase64(): String {
        return Base64.getEncoder().encodeToString(getOrCreateIdentityKeys().second.encoded)
    }

    /**
     * Seals a note to the recipient's public key using X25519 + ChaCha20-Poly1305.
     *
     * Format: [32-byte ephemeral public key] + [12-byte nonce] + [ciphertext with 16-byte Poly1305 tag]
     */
    fun sealNote(recipientPublicKeyB64: String, note: String): String {
        val recipientPubBytes = Base64.getDecoder().decode(recipientPublicKeyB64.trim())
        require(recipientPubBytes.size == 32) { "Invalid recipient public key length: ${recipientPubBytes.size}" }
        val recipientPubKey = X25519PublicKeyParameters(recipientPubBytes, 0)

        // Generate ephemeral keypair
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(random))
        val ephemeralKeyPair = generator.generateKeyPair()
        val ephemeralPriv = ephemeralKeyPair.private as X25519PrivateKeyParameters
        val ephemeralPub = ephemeralKeyPair.public as X25519PublicKeyParameters

        // Compute Diffie-Hellman shared secret
        val agreement = X25519Agreement()
        agreement.init(ephemeralPriv)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(recipientPubKey, sharedSecret, 0)

        // Derive 256-bit symmetric key with SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest(sharedSecret)

        // Generate 12-byte nonce
        val nonce = ByteArray(12)
        random.nextBytes(nonce)

        // Encrypt with ChaCha20-Poly1305
        val cipher = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 128, nonce)
        cipher.init(true, params)

        val plaintextBytes = note.toByteArray(Charsets.UTF_8)
        val ciphertext = ByteArray(cipher.getOutputSize(plaintextBytes.size))
        val len = cipher.processBytes(plaintextBytes, 0, plaintextBytes.size, ciphertext, 0)
        cipher.doFinal(ciphertext, len)

        // Assemble sealed payload
        val ephemeralPubBytes = ephemeralPub.encoded
        val payload = ByteArray(ephemeralPubBytes.size + nonce.size + ciphertext.size)
        System.arraycopy(ephemeralPubBytes, 0, payload, 0, ephemeralPubBytes.size)
        System.arraycopy(nonce, 0, payload, ephemeralPubBytes.size, nonce.size)
        System.arraycopy(ciphertext, 0, payload, ephemeralPubBytes.size + nonce.size, ciphertext.size)

        return Base64.getEncoder().encodeToString(payload)
    }

    /**
     * Opens an encrypted sealed note using the local private key.
     */
    fun openNote(ciphertextB64: String): String {
        val (localPriv, _) = getOrCreateIdentityKeys()
        val payload = Base64.getDecoder().decode(ciphertextB64.trim())
        require(payload.size >= 32 + 12 + 16) { "Ciphertext payload too short" }

        val ephemeralPubBytes = ByteArray(32)
        val nonce = ByteArray(12)
        val ciphertext = ByteArray(payload.size - 32 - 12)

        System.arraycopy(payload, 0, ephemeralPubBytes, 0, 32)
        System.arraycopy(payload, 32, nonce, 0, 12)
        System.arraycopy(payload, 44, ciphertext, 0, ciphertext.size)

        val ephemeralPubKey = X25519PublicKeyParameters(ephemeralPubBytes, 0)

        // Compute Diffie-Hellman shared secret
        val agreement = X25519Agreement()
        agreement.init(localPriv)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(ephemeralPubKey, sharedSecret, 0)

        // Derive 256-bit symmetric key with SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest(sharedSecret)

        // Decrypt with ChaCha20-Poly1305
        val cipher = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 128, nonce)
        cipher.init(false, params)

        val plaintext = ByteArray(cipher.getOutputSize(ciphertext.size))
        val len = cipher.processBytes(ciphertext, 0, ciphertext.size, plaintext, 0)
        val finalLen = cipher.doFinal(plaintext, len)

        return String(plaintext, 0, len + finalLen, Charsets.UTF_8)
    }
}
