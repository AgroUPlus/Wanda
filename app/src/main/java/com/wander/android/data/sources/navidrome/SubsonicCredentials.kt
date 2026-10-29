package com.wander.android.data.sources.navidrome

import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The Navidrome server and account, and the Subsonic salt+token auth built from them.
 *
 * Its own class, with no dependencies, so the HTTP clients can sign requests without depending
 * on [SubsonicApiClient], which itself depends on an HTTP client.
 */
@Singleton
class SubsonicCredentials @Inject constructor() {

    private class Account(val baseUrl: String, val username: String, val password: String)

    @Volatile
    private var account = Account("", "", "")

    val baseUrl: String get() = account.baseUrl

    val isConfigured: Boolean
        get() = account.let { it.baseUrl.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank() }

    fun configure(serverUrl: String, user: String, pass: String) {
        account = Account(serverUrl.trim().trimEnd('/'), user.trim(), pass.trim())
    }

    fun authParams(): Map<String, String> {
        val current = account
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }.toHex()
        return mapOf(
            "u" to current.username,
            "t" to md5(current.password + salt),
            "s" to salt,
            "v" to API_VERSION,
            "c" to CLIENT_NAME,
            "f" to "json"
        )
    }

    /**
     * [url] with auth added when it is an unsigned Subsonic request to the configured server, and
     * [url] unchanged otherwise.
     *
     * Artwork URLs are stored and shared without credentials, because they end up in the
     * database, in Jam rooms, in handoffs and on the media session where other apps can read
     * them. They are signed here, at the moment they are fetched, and only for this server.
     */
    fun sign(url: HttpUrl): HttpUrl =
        if (isUnsignedRequestToServer(url)) {
            url.newBuilder().apply { authParams().forEach { (k, v) -> setQueryParameter(k, v) } }.build()
        } else {
            url
        }

    private fun isUnsignedRequestToServer(url: HttpUrl): Boolean {
        val base = account.baseUrl.toHttpUrlOrNull() ?: return false
        val sameOrigin = url.scheme == base.scheme && url.host == base.host && url.port == base.port
        val subsonicPath = url.encodedPath.startsWith(base.encodedPath.trimEnd('/') + "/rest/")
        return isConfigured && sameOrigin && subsonicPath && url.queryParameter("t") == null
    }

    private companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "Wanda"
        const val SALT_BYTES = 8

        /**
         * MD5 is not a choice here. Subsonic 1.16.1 defines authentication as
         * `t=md5(password + salt)` with the salt sent alongside in the clear, and a Navidrome or
         * Subsonic server will reject anything else — there is no stronger digest to negotiate.
         * The salt is 8 bytes of [SecureRandom] and is fresh per request, so the token is not
         * replayable across calls, and the exchange only ever happens over HTTPS.
         */
        @Suppress("kotlin:S4790") // Protocol-mandated digest, not a password hash of our choosing.
        fun md5(input: String): String =
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).toHex()

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
