package com.wander.android.core.security

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The account's settings vault: a key this device holds and the Agro server does not.
 *
 * The server used to encrypt synced settings with a key it generated and stored in the users table,
 * one column away from the ciphertext it opened. Anyone who read the database read both, so a
 * stolen disk or a leaked backup gave up every account's upstream server address and username. That
 * arrangement also happened to be broken — the write path and the read path used different columns,
 * and decryption failed *open* — but fixing the bug would not have fixed the design.
 *
 * So the key moves here. Settings are sealed on the device and the server stores an opaque blob.
 * What the server keeps is [wrapKey]'s output: the vault key sealed under a key derived from the
 * account passphrase. Deriving that needs the passphrase, and the server keeps only an Argon2 hash
 * of it, so the wrapped key is inert there — while a user who has lost every device can still type
 * the passphrase on a new one and get their settings back.
 *
 * ### Why the passphrase is not simply the key
 *
 * Because this app deliberately does not keep it. Pairing exchanges the passphrase for a revocable
 * device token and discards it, and a device paired by QR code never sees it at all. A key derived
 * fresh at each unlock would lock those devices out. The vault key is therefore independent, kept
 * in [SecureStorage] (Android Keystore), and the passphrase only ever wraps it.
 *
 * ### Choices
 *
 * **Argon2id** for the wrapping key, because the thing it defends is a four-word passphrase from a
 * 191-word list — around thirty bits. Against an attacker holding the database, the only thing
 * between that and the vault is how expensive each guess is, and a memory-hard function is the one
 * knob that makes a GPU farm stop being cheap. Parameters follow the RFC 9106 second recommendation
 * (64 MiB, three passes), which a phone can afford once at login.
 *
 * **AES-256-GCM** for both sealing layers rather than ChaCha20-Poly1305: it is hardware-accelerated
 * on every device this app runs on, and the JCA has had it since well before this app's minimum SDK,
 * whereas ChaCha20-Poly1305 only arrives at API 28. One primitive, no fallback path.
 *
 * Every operation here is CPU-bound and [deriveWrappingKey] deliberately so — call them off the
 * main thread.
 */
object AgroVault {

    /** RFC 9106's second recommended parameter set, which a phone can afford once per login. */
    private const val ARGON2_MEMORY_KIB = 65_536
    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_PARALLELISM = 4

    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    // Domain separation contexts for HKDF-SHA256 (RFC 5869)
    const val INFO_SETTINGS = "agro/v1/settings"
    const val INFO_PRESENCE = "agro/v1/presence"
    const val INFO_P2P_RELAY = "agro/v1/p2p-relay"

    /** Thrown when sealed data cannot be opened. Never swallowed: see [openSettings]. */
    class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** A fresh vault key, for an account that has not enrolled one yet. */
    fun newVaultKey(): ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

    /** A fresh per-account salt, stored beside the wrapped key. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /**
     * The key that seals the vault key, derived from the account passphrase.
     *
     * Slow on purpose — hundreds of milliseconds — because that cost is multiplied by every guess
     * an attacker with the database has to make.
     */
    fun deriveWrappingKey(passphrase: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(ARGON2_MEMORY_KIB)
            .withIterations(ARGON2_ITERATIONS)
            .withParallelism(ARGON2_PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(KEY_BYTES)
        generator.generateBytes(passphrase.trim().toByteArray(Charsets.UTF_8), out)
        return out
    }

    /** Seals the vault key under the passphrase-derived key, for the server to hold. */
    fun wrapKey(vaultKey: ByteArray, wrappingKey: ByteArray): String = seal(vaultKey, wrappingKey)

    /**
     * Recovers the vault key from what the server returned at login.
     *
     * @throws VaultException if the passphrase is wrong or the envelope is damaged.
     */
    fun unwrapKey(wrapped: String, wrappingKey: ByteArray): ByteArray =
        open(wrapped, wrappingKey, "vault key")

    /** Seals settings for the server to store without reading. */
    fun sealSettings(plaintext: String, vaultKey: ByteArray): String = seal(
        plaintext.toByteArray(Charsets.UTF_8),
        getSettingsKey(vaultKey)
    )

    /**
     * Opens settings the server handed back.
     *
     * Fails loudly. The predecessor of this code returned the input unchanged when decryption
     * failed, which is how ciphertext ended up being handed to the app as a server URL and nobody
     * noticed for two migrations. A vault that cannot be opened is an error, not a value.
     *
     * @throws VaultException if the blob is not ours or the key is wrong.
     */
    fun openSettings(blob: String, vaultKey: ByteArray): String =
        String(open(blob, getSettingsKey(vaultKey), "settings"), Charsets.UTF_8)

    // ── Key Separation & Derivation (HKDF-SHA256, RFC 5869) ───────────────────────────────────

    /**
     * Derives a purpose-specific subkey from the root [vaultKey] using HKDF-SHA256.
     * Prevents cross-context domain confusion and key-reuse attacks.
     */
    fun deriveSubkey(rootKey: ByteArray, info: String, outputBytes: Int = KEY_BYTES): ByteArray {
        require(rootKey.size == KEY_BYTES) { "root key must be $KEY_BYTES bytes" }
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        // HKDF Extract & Expand with empty salt (RFC 5869 allows null/empty salt when IKM is already high entropy)
        hkdf.init(HKDFParameters(rootKey, null, info.toByteArray(Charsets.UTF_8)))
        val out = ByteArray(outputBytes)
        hkdf.generateBytes(out, 0, outputBytes)
        return out
    }

    /**
     * Overwrites a derived subkey once it has been used.
     *
     * Only worth doing for keys this code owns the lifetime of — the per-call subkeys from
     * [deriveSubkey], which have no other holder. The root key is kept in `SecureStorage` because
     * something has to be able to derive the next subkey, and wiping a copy of it would not
     * shorten the life of the original.
     *
     * Not a guarantee about the whole process: the JVM may have moved the array while it was live,
     * and a `String` made from key material cannot be wiped at all, which is why nothing here ever
     * turns one into a `String`. What it does buy is that the obvious copy does not sit in the heap
     * for the lifetime of the app waiting to appear in a dump.
     */
    fun wipe(key: ByteArray) {
        java.util.Arrays.fill(key, 0.toByte())
    }

    fun getSettingsKey(vaultKey: ByteArray): ByteArray = deriveSubkey(vaultKey, INFO_SETTINGS)
    fun getPresenceKey(vaultKey: ByteArray): ByteArray = deriveSubkey(vaultKey, INFO_PRESENCE)
    fun getP2pRelayKey(vaultKey: ByteArray): ByteArray = deriveSubkey(vaultKey, INFO_P2P_RELAY)

    /** Seals arbitrary byte payloads (presence envelopes, metadata) under a subkey. */
    fun sealPayload(plaintext: ByteArray, key: ByteArray): String = seal(plaintext, key)

    /** Opens an arbitrary sealed payload under a subkey. */
    fun openPayload(sealed: String, key: ByteArray, context: String = "payload"): ByteArray =
        open(sealed, key, context)

    // ── AES-256-GCM, as `base64(nonce || ciphertext||tag)` ───────────────────────────────────

    private fun seal(plaintext: ByteArray, key: ByteArray): String {
        require(key.size == KEY_BYTES) { "a vault key is $KEY_BYTES bytes" }
        // A fresh nonce every time. GCM's failure mode for a repeated (key, nonce) pair is total,
        // so this is never derived from anything and never reused.
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        val sealed = cipher.doFinal(plaintext)
        return encodeBase64(nonce + sealed)
    }

    private fun open(encoded: String, key: ByteArray, what: String): ByteArray {
        require(key.size == KEY_BYTES) { "a vault key is $KEY_BYTES bytes" }
        val raw = try {
            decodeBase64(encoded)
        } catch (e: IllegalArgumentException) {
            throw VaultException("the sealed $what is not valid base64", e)
        }
        if (raw.size <= NONCE_BYTES) throw VaultException("the sealed $what is too short to be real")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, raw, 0, NONCE_BYTES)
            )
            cipher.doFinal(raw, NONCE_BYTES, raw.size - NONCE_BYTES)
        } catch (e: GeneralSecurityException) {
            throw VaultException("the sealed $what did not open — wrong passphrase, or damaged", e)
        }
    }

    // `java.util.Base64` rather than `android.util.Base64`: it exists from API 26, which is this
    // app's minimum, and unlike the Android one it is real on the JVM, so this class is testable
    // without an emulator or Robolectric.
    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
