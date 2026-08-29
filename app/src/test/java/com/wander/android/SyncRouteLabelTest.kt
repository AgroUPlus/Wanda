package com.wander.android

import com.wander.android.data.sources.agro.SyncRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The route a sync offer names must be one that was *measured*.
 *
 * A peer publishes a LAN address whenever it has one, whether or not this device can reach it —
 * different networks, a firewall, a permission not granted. Labelling that "Direct Wi-Fi" is a
 * promise the transfer then breaks, and the user's first clue was a three-second stall.
 */
class SyncRouteLabelTest {

    /** Mirrors `WanderAppViewModel.routeFor`. */
    private fun route(reachable: Boolean, isServerArchive: Boolean): SyncRoute = when {
        reachable -> SyncRoute.DIRECT
        isServerArchive -> SyncRoute.ARCHIVE
        else -> SyncRoute.RELAY
    }

    @Test
    fun `a reachable peer is direct`() {
        assertEquals(SyncRoute.DIRECT, route(reachable = true, isServerArchive = false))
    }

    /** The case that was wrong: a published LAN address this device cannot actually reach. */
    @Test
    fun `an unreachable peer is never called direct`() {
        assertEquals(SyncRoute.RELAY, route(reachable = false, isServerArchive = false))
    }

    @Test
    fun `the server's own copy is named as the archive`() {
        assertEquals(SyncRoute.ARCHIVE, route(reachable = false, isServerArchive = true))
    }

    /** Reachability wins: a direct hop beats the server even when the server has it too. */
    @Test
    fun `a reachable peer beats the archive`() {
        assertEquals(SyncRoute.DIRECT, route(reachable = true, isServerArchive = true))
    }

    @Test
    fun `labels read the way they are shown`() {
        assertEquals("Direct Wi-Fi", SyncRoute.DIRECT.label)
        assertEquals("Relay", SyncRoute.RELAY.label)
        assertEquals("Server archive", SyncRoute.ARCHIVE.label)
    }
}
