package com.wander.android.data.podcast

import okhttp3.Response

/**
 * Where a feed has permanently moved, read from the redirects OkHttp followed to reach [response].
 *
 * Only the leading run of permanent hops (301, 308) counts: a temporary redirect after them says
 * the next address is not to be remembered, so the move stops at the last permanent target. Null
 * when the first hop was not permanent, or there was no redirect at all.
 */
internal fun permanentRedirectTarget(response: Response): String? {
    val hops = generateSequence(response.priorResponse) { it.priorResponse }.toList().asReversed()
    var target: String? = null
    for ((index, hop) in hops.withIndex()) {
        if (hop.code != HTTP_MOVED_PERMANENTLY && hop.code != HTTP_PERMANENT_REDIRECT) break
        val next = hops.getOrNull(index + 1) ?: response
        target = next.request.url.toString()
    }
    return target
}

private const val HTTP_MOVED_PERMANENTLY = 301
private const val HTTP_PERMANENT_REDIRECT = 308
