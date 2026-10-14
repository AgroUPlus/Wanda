package com.wander.android.core.database

import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * A stand-in for the Android keystore, which Robolectric does not provide.
 *
 * Wanda's `Application` builds `SecureStorage` on start-up, and that asks the keystore for a master
 * key. Keys here live in memory as ordinary AES keys, which is all the code under test needs; none
 * of this security is real, and it is installed only into the test JVM.
 */
object FakeAndroidKeyStore {
    private const val NAME = "AndroidKeyStore"
    private val keys: MutableMap<String, SecretKey> = java.util.concurrent.ConcurrentHashMap()

    fun install() {
        if (Security.getProvider(NAME) != null) return
        Security.addProvider(FakeProvider())
    }

    private class FakeProvider : Provider(NAME, 1.0, "In-memory keystore for tests") {
        init {
            put("KeyStore.$NAME", FakeKeyStoreSpi::class.java.name)
            put("KeyGenerator.AES", FakeKeyGeneratorSpi::class.java.name)
        }
    }

    class FakeKeyStoreSpi : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? = keys[alias]
        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String?): Certificate? = null
        override fun engineGetCreationDate(alias: String?): Date? = if (alias in keys) Date() else null
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) {
            keys[alias] = key as SecretKey
        }
        override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<Certificate>?) = Unit
        override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = Unit
        override fun engineDeleteEntry(alias: String) {
            keys.remove(alias)
        }
        override fun engineAliases(): java.util.Enumeration<String> = Collections.enumeration(keys.keys.toList())
        override fun engineContainsAlias(alias: String?): Boolean = alias in keys
        override fun engineSize(): Int = keys.size
        override fun engineIsKeyEntry(alias: String?): Boolean = alias in keys
        override fun engineIsCertificateEntry(alias: String?): Boolean = false
        override fun engineGetCertificateAlias(cert: Certificate?): String? = null
        override fun engineStore(stream: java.io.OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: java.io.InputStream?, password: CharArray?) = Unit
        override fun engineEntryInstanceOf(alias: String?, entryClass: Class<out KeyStore.Entry>?): Boolean =
            alias in keys && entryClass == KeyStore.SecretKeyEntry::class.java
        override fun engineGetEntry(
            alias: String,
            protParam: KeyStore.ProtectionParameter?
        ): KeyStore.Entry? = keys[alias]?.let { KeyStore.SecretKeyEntry(it) }
    }

    class FakeKeyGeneratorSpi : KeyGeneratorSpi() {
        private var alias: String? = null
        private var bits = 256

        override fun engineInit(random: SecureRandom?) = Unit
        override fun engineInit(params: java.security.spec.AlgorithmParameterSpec?, random: SecureRandom?) {
            // A KeyGenParameterSpec names the alias the key must be stored under.
            alias = params?.javaClass?.getMethod("getKeystoreAlias")?.invoke(params) as? String
            bits = (params?.javaClass?.getMethod("getKeySize")?.invoke(params) as? Int) ?: bits
        }
        override fun engineInit(keysize: Int, random: SecureRandom?) {
            bits = keysize
        }
        override fun engineGenerateKey(): SecretKey {
            val key = SecretKeySpec(ByteArray(bits / 8).also { SecureRandom().nextBytes(it) }, "AES")
            alias?.let { keys[it] = key }
            return key
        }
    }
}
