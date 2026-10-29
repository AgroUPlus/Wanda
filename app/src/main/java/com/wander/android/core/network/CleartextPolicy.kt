package com.wander.android.core.network

/**
 * Which hosts may be reached over plain HTTP.
 *
 * `network_security_config.xml` permits cleartext globally at the manifest level because P2P
 * peers and self-hosted LAN servers live at dynamic IPs no static domain list can enumerate.
 * That leaves nothing stopping plain HTTP to a *public* host. This is the actual enforcement:
 * cleartext is only ever allowed to a private/loopback/mDNS peer.
 *
 * Private ranges are matched on numeric IPv4 addresses only. A name such as `10.example.com` or
 * `192.168.evil.com` is a public DNS name that can resolve anywhere, so it gets no exemption.
 */
internal object CleartextPolicy {

    // NOSONAR: loopback/emulator allowlist for the cleartext-HTTP check.
    private val LOOPBACK_NAMES = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2") // NOSONAR

    fun allows(host: String): Boolean {
        val h = host.trim('[', ']').lowercase()
        return h in LOOPBACK_NAMES || h.endsWith(".local") || isPrivateIpv4(h)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split(".").map { it.toIntOrNull() ?: -1 }
        if (octets.size != IPV4_OCTETS || octets.any { it !in 0..MAX_OCTET }) return false
        return PRIVATE_IPV4.any { (first, second) -> octets[0] == first && octets[1] in second }
    }

    /** First octet, and the second octets that make it private: RFC 1918 and link-local. */
    private val PRIVATE_IPV4 = listOf(
        10 to 0..255,
        172 to 16..31,
        192 to 168..168,
        169 to 254..254
    )

    private const val IPV4_OCTETS = 4
    private const val MAX_OCTET = 255
}
