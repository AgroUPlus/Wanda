package com.wander.android.core.sync

/**
 * Which identity key a peer stream may be sealed to.
 *
 * Its own file, and pure, because it is the whole of the guarantee that a bearer grant now carries
 * an identity — and because a `P2PServer` cannot be built on the JVM, so a rule left inside it
 * could only ever be checked on two physical phones. The failure it prevents is silent: sealing to
 * the wrong key still returns audio, to the wrong person.
 */
internal object GrantBinding {

    /**
     * Answers the key to seal to, or null when nothing may be sealed.
     *
     * [boundKeys] is what the grant names — the identity keys Agro published for the listener. When
     * it is non-empty the header is *checked against* it rather than trusted: an attacker who
     * rewrites the request in flight can put their own key in `X-Wanda-Identity`, but it will not
     * be in the set, so it is not the key the room key is sealed to.
     *
     * An empty [boundKeys] means the grant names no identity, and then [headerKey] stands alone as
     * it always did. That covers two real cases and neither is a weakness worth breaking: off-grid,
     * where `mintPairingGrant` bound the grant to the peer's key itself, and an older Agro that
     * sends no key list, where there is nothing to check against and refusing would break a working
     * setup to close a gap that server cannot help with.
     */
    fun sealingKey(boundKeys: List<String>, headerKey: String?): String? {
        if (boundKeys.isEmpty()) return headerKey
        if (headerKey != null && headerKey in boundKeys) return headerKey
        // Nothing usable was offered, but the grant still names who it is for. Sealing to a bound
        // key serves the listener rather than failing them, and is safe precisely because that key
        // came from Agro rather than from the request.
        return boundKeys.firstOrNull()
    }
}
