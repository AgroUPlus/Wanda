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
     * The order is the contract the status line reports against, and the reason a listener is told
     * "streamed over Wi-Fi" rather than being left to guess where the audio came from.
     */
    @Test
    fun `the tiers are ordered cheapest and most faithful first`() {
        assertEquals(
            listOf(
                ResolvedFrom.LOCAL_STORAGE,
                ResolvedFrom.NAVIDROME,
                ResolvedFrom.YOUTUBE_MUSIC,
                ResolvedFrom.P2P_DIRECT,
                ResolvedFrom.AGRO_RELAY
            ),
            ResolvedFrom.entries.toList()
        )
    }
}
