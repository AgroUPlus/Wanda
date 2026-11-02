package com.wander.android.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkHostTest {

    @Test
    fun lanAndTailnetHostsAreLocal() {
        listOf(
            "192.168.1.20", "10.0.0.5", "nas.local", "localhost",
            "100.64.0.1", "100.101.102.103", "100.127.255.254", "music.tail1234.ts.net"
        ).forEach { assertTrue(it, LocalNetworkHost.isLocal(it)) }
    }

    @Test
    fun publicHostsAreNot() {
        listOf(
            "100.63.255.255", "100.128.0.1", "8.8.8.8", "music.example.com", "100.64.evil.com"
        ).forEach { assertFalse(it, LocalNetworkHost.isLocal(it)) }
    }

    /** The tailnet only counts as reachable; it gains no cleartext exemption. */
    @Test
    fun theTailnetIsNotACleartextExemption() {
        assertFalse(CleartextPolicy.allows("100.64.0.1"))
    }
}
