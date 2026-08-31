package com.wander.android
 
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyDomainValidationTest {

    private val allowedDomains = listOf("archive.org", "lrclib.net", "nyaa.si")

    private fun isAllowedProxyDomain(host: String): Boolean {
        val cleanHost = host.trim().lowercase()
        if (cleanHost.isEmpty()) return false
        return allowedDomains.any { d -> cleanHost == d || cleanHost.endsWith(".$d") }
    }

    @Test
    fun `legitimate whitelisted domains and subdomains are allowed`() {
        assertTrue(isAllowedProxyDomain("archive.org"))
        assertTrue(isAllowedProxyDomain("ia8000.us.archive.org"))
        assertTrue(isAllowedProxyDomain("lrclib.net"))
        assertTrue(isAllowedProxyDomain("api.lrclib.net"))
        assertTrue(isAllowedProxyDomain("nyaa.si"))
        assertTrue(isAllowedProxyDomain("s.nyaa.si"))
    }

    @Test
    fun `ssrf subdomain attacks and malicious spoofed domains are rejected`() {
        assertFalse(isAllowedProxyDomain("evil-archive.org.attacker.com"))
        assertFalse(isAllowedProxyDomain("archive.org.malicious.net"))
        assertFalse(isAllowedProxyDomain("fake-lrclib.net"))
        assertFalse(isAllowedProxyDomain("lrclib.net.evil.com"))
        assertFalse(isAllowedProxyDomain("attacker.nyaa.si.fake"))
        assertFalse(isAllowedProxyDomain("google.com"))
        assertFalse(isAllowedProxyDomain("127.0.0.1"))
        assertFalse(isAllowedProxyDomain("localhost"))
        assertFalse(isAllowedProxyDomain(""))
    }
}
