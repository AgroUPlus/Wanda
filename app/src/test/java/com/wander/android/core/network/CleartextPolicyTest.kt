package com.wander.android.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {

    @Test
    fun allowsLoopbackPrivateAndMdnsHosts() {
        listOf(
            "localhost", "127.0.0.1", "[::1]", "10.0.2.2", "10.1.2.3", "192.168.49.1",
            "169.254.10.10", "172.16.0.1", "172.31.255.255", "nas.local", "NAS.LOCAL"
        ).forEach { assertTrue(it, CleartextPolicy.allows(it)) }
    }

    @Test
    fun refusesPublicHostsThatLookPrivate() {
        listOf(
            "10.example.com", "192.168.evil.com", "10.0.0.1.nip.io", "172.16.example.org",
            "172.32.0.1", "8.8.8.8", "192.169.1.1", "10.256.0.1", "example.com", "local.example.com"
        ).forEach { assertFalse(it, CleartextPolicy.allows(it)) }
    }
}
