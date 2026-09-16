package com.wander.android.data.repository

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Shared query encoder and decoder for universal deep links (`wanda://album`, `wanda://track`).
 *
 * Hand-rolled rather than `Uri.Builder` so parsing and encoding are unit testable on the JVM
 * without requiring Android platform stubs.
 */
internal object UniversalLinkCodec {

    fun parseQuery(uri: String): Map<String, String> {
        val query = uri.trim().substringAfter("?", missingDelimiterValue = "")
        if (query.isEmpty()) return emptyMap()

        return query.split("&")
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val key = pair.substring(0, eq)
                val value = pair.substring(eq + 1)
                key to decode(value)
            }
            .toMap()
    }

    fun buildQuery(parameters: List<Pair<String, String>>): String =
        parameters.joinToString("&") { (key, value) -> "$key=${encode(value)}" }

    fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
