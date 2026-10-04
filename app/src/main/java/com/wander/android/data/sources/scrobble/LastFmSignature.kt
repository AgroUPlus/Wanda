package com.wander.android.data.sources.scrobble

import java.security.MessageDigest

/**
 * Last.fm's `api_sig`: every parameter but `format` and `callback`, sorted by name, written as
 * `<name><value>` one after another, the shared secret appended, and the MD5 of that in hex.
 * See last.fm/api/desktopauth, section 6.
 */
internal object LastFmSignature {
    private val UNSIGNED = setOf("format", "callback")

    fun sign(params: Map<String, String>, secret: String): String {
        val text = params.filterKeys { it !in UNSIGNED }
            .toSortedMap()
            .entries
            .joinToString("") { (name, value) -> name + value } + secret
        return MessageDigest.getInstance("MD5") // NOSONAR: api_sig is defined by Last.fm as MD5; not used for integrity or passwords
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
