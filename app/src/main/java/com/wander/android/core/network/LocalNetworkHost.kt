package com.wander.android.core.network

/**
 * Hosts that can answer without the internet: the private, loopback and `.local` ones
 * [CleartextPolicy] exempts, plus a Tailscale tailnet (`100.64.0.0/10`, `*.ts.net`).
 *
 * Used only to decide whether a request is *worth trying* on a network that has not validated —
 * a home Wi-Fi with its uplink down, a travel router, a tailnet. It grants nothing: cleartext stays
 * [CleartextPolicy]'s decision, which is why the tailnet is not added there.
 */
internal object LocalNetworkHost {

    fun isLocal(host: String): Boolean {
        val h = host.trim('[', ']').lowercase()
        return CleartextPolicy.allows(h) || h.endsWith(".ts.net") || isTailnetIpv4(h)
    }

    /** Tailscale hands out addresses from the RFC 6598 shared space, 100.64.0.0/10. */
    private fun isTailnetIpv4(host: String): Boolean {
        val octets = host.split(".").map { it.toIntOrNull() ?: -1 }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 100 && octets[1] in 64..127
    }
}
