package com.wander.android

import com.wander.android.data.repository.ListenAlongResolver
import com.wander.android.data.repository.ResolvedFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two tiers below the streaming ones are the ones that can fail expensively: a direct attempt
 * that cannot work costs a connection timeout on every track change, and a relay opened for a hash
 * nobody has occupies the server for nothing. Both gates are all-or-nothing, and these pin that.
 */
class ListenAlongResolverTest {

    @Test
    fun `a direct transfer needs an address, a token and a hash`() {
        assertTrue(ListenAlongResolver.canTryDirect("192.168.1.9:8702", "tok", "abc123"))
    }

    @Test
    fun `a direct transfer is refused when any one of the three is missing`() {
        // No address: the server did not judge these two to be on one network.
        assertFalse(ListenAlongResolver.canTryDirect(null, "tok", "abc123"))
        // No token: the peer's server would refuse the request, so do not make it.
        assertFalse(ListenAlongResolver.canTryDirect("192.168.1.9:8702", null, "abc123"))
        // No hash: the host is streaming, and a peer answers for files rather than for titles.
        assertFalse(ListenAlongResolver.canTryDirect("192.168.1.9:8702", "tok", null))
        // Blank is not a value.
        assertFalse(ListenAlongResolver.canTryDirect("", "tok", "abc123"))
        assertFalse(ListenAlongResolver.canTryDirect("192.168.1.9:8702", "  ", "abc123"))
    }

    /**
     * The off-grid tier is the only one whose credential does not come from the server, because it
     * is the only one that has to work with no server in reach. `OffGridPairing` obtains it from
     * the peer over the radio link itself.
     */
    @Test
    fun `off-grid prefers the grant the peer issued face to face`() {
        assertEquals("peer", ListenAlongResolver.offGridToken("peer", "agro"))
    }

    @Test
    fun `off-grid still accepts an Agro token when there is no peer grant`() {
        assertEquals("agro", ListenAlongResolver.offGridToken(null, "agro"))
        assertEquals("agro", ListenAlongResolver.offGridToken("  ", "agro"))
    }

    @Test
    fun `off-grid has no bearer when neither side issued one`() {
        assertEquals(null, ListenAlongResolver.offGridToken(null, null))
        assertEquals(null, ListenAlongResolver.offGridToken("", "  "))
    }

    @Test
    fun `the relay needs a device and a hash but no lan address`() {
        assertTrue(ListenAlongResolver.canTryRelay("host-phone", "abc123", serverConfigured = true))
    }

    @Test
    fun `the relay is refused without a hash, a device, or a server`() {
        assertFalse(ListenAlongResolver.canTryRelay("host-phone", null, serverConfigured = true))
        assertFalse(ListenAlongResolver.canTryRelay(null, "abc123", serverConfigured = true))
        assertFalse(ListenAlongResolver.canTryRelay("host-phone", "abc123", serverConfigured = false))
    }

    /**
     * The order is the contract the transport badge reports against, and the reason a listener can
     * see whether the audio is costing data, needs a router, or is encrypted.
     *
     * Off-grid sits below the LAN and above the relay deliberately. It beats the relay on every
     * axis that matters — nothing leaves the two devices, and it is an order of magnitude faster —
     * but it costs a radio link and usually a tap on a system dialog, so it must not be attempted
     * while a network both devices are already on would have done.
     */
    @Test
    fun `the tiers are ordered cheapest and most faithful first`() {
        assertEquals(
            listOf(
                ResolvedFrom.LOCAL_STORAGE,
                ResolvedFrom.NAVIDROME,
                ResolvedFrom.YOUTUBE_MUSIC,
                ResolvedFrom.P2P_DIRECT,
                ResolvedFrom.P2P_OFFGRID,
                ResolvedFrom.AGRO_RELAY
            ),
            ResolvedFrom.entries.toList()
        )
    }
}
