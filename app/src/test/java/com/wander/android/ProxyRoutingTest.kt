package com.wander.android

import com.wander.android.core.network.ProxyRouting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which requests go through the privacy relay, and — the point of the test — which do not.
 *
 * A relay that under-matches costs privacy quietly. A relay that over-matches sends a stranger's
 * host through the user's own server on their API key. Both failures are invisible at runtime,
 * which is why the decision was extracted from the interceptor to be asked directly.
 */
class ProxyRoutingTest {

    @Test
    fun `the relayed domains are relayed`() {
        assertTrue(ProxyRouting.shouldRelay("lrclib.net", "/api/get"))
        assertTrue(ProxyRouting.shouldRelay("nyaa.si", "/"))
    }

    /** Backends move hosts around; a subdomain of a relayed domain is still that domain. */
    @Test
    fun `subdomains of a relayed domain are relayed`() {
        assertTrue(ProxyRouting.shouldRelay("api.lrclib.net", "/api/get"))
        assertTrue(ProxyRouting.shouldRelay("ia800.us.archive.org", "/metadata/x"))
    }

    /**
     * The bug this replaced.
     *
     * `host.contains("lrclib.net")` matched the string anywhere in the name, so a host somebody
     * else controls could route a request through the user's own Agro server, carrying their API
     * key. Not exploitable as it stood — Agro validates the target strictly at the far end — but a
     * substring test standing in for a host comparison in a privacy path is a bug waiting for
     * whoever moves the code next.
     */
    @Test
    fun `a host that merely contains a relayed domain is not relayed`() {
        assertFalse(ProxyRouting.shouldRelay("lrclib.net.example.com", "/api/get"))
        assertFalse(ProxyRouting.shouldRelay("notlrclib.net", "/api/get"))
        assertFalse(ProxyRouting.shouldRelay("evil-archive.org", "/metadata/x"))
    }

    /** Case is not part of a host's identity, and a redirect can change it. */
    @Test
    fun `host matching ignores case`() {
        assertTrue(ProxyRouting.shouldRelay("LRCLIB.NET", "/api/get"))
        assertTrue(ProxyRouting.shouldRelay("API.Archive.Org", "/metadata/x"))
    }

    /**
     * Archive.org is relayed for catalogue lookups only — the rest is audio and artwork, which the
     * relay cannot carry because Agro buffers whole bodies in memory.
     */
    @Test
    fun `only archive metadata is relayed`() {
        assertTrue(ProxyRouting.shouldRelay("archive.org", "/advancedsearch.php?q=x"))
        assertTrue(ProxyRouting.shouldRelay("archive.org", "/metadata/some-item"))
        assertFalse(ProxyRouting.shouldRelay("archive.org", "/download/some-item/track.mp3"))
        assertFalse(ProxyRouting.shouldRelay("archive.org", "/services/img/some-item"))
    }

    /** The path is matched at its start, not anywhere in it. */
    @Test
    fun `a path that merely mentions metadata is not relayed`() {
        assertFalse(ProxyRouting.shouldRelay("archive.org", "/download/metadata/track.mp3"))
    }

    /**
     * Recorded rather than asserted as desirable: YouTube Music is the app's largest source of
     * outbound requests and is not relayed at all. When that is fixed this test should fail, and
     * the failure is the reminder to delete it.
     */
    @Test
    fun `youtube music is not relayed today`() {
        assertFalse(ProxyRouting.shouldRelay("music.youtube.com", "/youtubei/v1/browse"))
    }

    @Test
    fun `everything else goes direct`() {
        assertFalse(ProxyRouting.shouldRelay("example.com", "/"))
        assertFalse(ProxyRouting.shouldRelay("", ""))
    }
}
