package com.wander.android.core.network

/**
 * Which outbound requests go through the Agro privacy relay.
 *
 * Pulled out of the OkHttp interceptor so the decision can be tested. It was three conditions
 * inlined in a lambda that needs a whole HTTP client to exercise, which is why the substring bug
 * below survived: nothing could ask it a question.
 */
object ProxyRouting {

    /**
     * Whether a request to [host] and [path] should be relayed.
     *
     * Only metadata. Streaming audio through the relay is a separate, unbuilt feature — Agro
     * buffers whole response bodies in memory today, so an album would be tens of megabytes of
     * server RAM per listener.
     */
    fun shouldRelay(host: String, path: String): Boolean = when {
        matchesDomain(host, ARCHIVE_ORG) -> isArchiveMetadata(path)
        else -> RELAYED_DOMAINS.any { matchesDomain(host, it) }
    }

    /**
     * Whether [host] *is* [domain], or a subdomain of it.
     *
     * The point of the function. This used to be `host.contains(domain)`, which matches on any
     * appearance of the string anywhere in the name — so `lrclib.net.example.com` was relayed, and
     * so was `notlrclib.net`. It is not exploitable as it stands, because the relay is an outbound
     * routing decision and Agro validates the target strictly at the other end. It is still a
     * substring test standing in for a host comparison in a privacy path, and the next person to
     * move this code has no reason to suspect it of being one.
     *
     * The dot matters: without it `evil-archive.org` would pass a naive `endsWith`.
     */
    private fun matchesDomain(host: String, domain: String): Boolean =
        host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)

    /**
     * Archive.org is relayed for catalogue lookups only.
     *
     * Its audio and artwork go direct, and deliberately: they are the large bodies the relay cannot
     * carry. Worth knowing that this means archive.org still sees the listener's address for
     * anything they actually play.
     */
    private fun isArchiveMetadata(path: String): Boolean =
        path.startsWith("/advancedsearch.php") || path.startsWith("/metadata/")

    private const val ARCHIVE_ORG = "archive.org"

    /**
     * Relayed in full.
     *
     * Notably absent: YouTube Music, which is the app's largest source of outbound requests and is
     * not relayed at all. See the tracking issue — closing that is a client-sized change, not an
     * entry in this list.
     */
    private val RELAYED_DOMAINS = listOf("lrclib.net", "nyaa.si")
}
