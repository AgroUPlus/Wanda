package com.wander.android

import com.wander.android.core.p2p.NearbyPeers
import com.wander.android.core.p2p.OffGridBeacon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire format shouted into a room, and what a scanner makes of what it hears.
 *
 * The privacy property is the one worth pinning: an advertisement is broadcast continuously to
 * anyone in range, so what it carries is what a stranger with a scanner can collect. A regression
 * that put a device name in here would be invisible in every other test.
 */
class OffGridBeaconTest {

    private val key = ByteArray(32) { (it + 1).toByte() }

    private fun beacon(servesAudio: Boolean = true) = OffGridBeacon(
        deviceId = OffGridBeacon.deviceIdFrom(key),
        fingerprint = OffGridBeacon.fingerprintFrom(key),
        servesAudio = servesAudio
    )

    @Test
    fun `a beacon survives the round trip`() {
        val original = beacon()
        assertEquals(original, OffGridBeacon.fromBytes(original.toBytes()))
    }

    /** It has to fit in a BLE advertisement's service data, which is around twenty bytes. */
    @Test
    fun `a beacon fits in one advertisement`() {
        assertTrue("beacon is ${beacon().toBytes().size} bytes", beacon().toBytes().size <= 20)
    }

    /** Nothing in the payload should be able to name a person or a phone. */
    @Test
    fun `a beacon carries no name`() {
        val bytes = beacon().toBytes()
        assertEquals(OffGridBeacon.SIZE, bytes.size)
        // Everything after the flag byte would be room for one; there is none.
        assertEquals(14, bytes.size)
    }

    @Test
    fun `the serving flag survives`() {
        assertEquals(false, OffGridBeacon.fromBytes(beacon(servesAudio = false).toBytes())?.servesAudio)
        assertEquals(true, OffGridBeacon.fromBytes(beacon(servesAudio = true).toBytes())?.servesAudio)
    }

    /** A scan hears everything in the air; foreign payloads are the normal case, not an error. */
    @Test
    fun `foreign and malformed payloads are ignored`() {
        assertNull(OffGridBeacon.fromBytes(null))
        assertNull(OffGridBeacon.fromBytes(ByteArray(3)))
        assertNull(OffGridBeacon.fromBytes(ByteArray(OffGridBeacon.SIZE) { 99 }))
    }

    /** Derived from the key so it survives a restart without being stored anywhere. */
    @Test
    fun `the device id is stable for one identity and differs between two`() {
        val other = ByteArray(32) { (it + 9).toByte() }
        assertEquals(OffGridBeacon.deviceIdFrom(key), OffGridBeacon.deviceIdFrom(key))
        assertTrue(OffGridBeacon.deviceIdFrom(key) != OffGridBeacon.deviceIdFrom(other))
    }
}

/** Turning a stream of sightings into "who is in the room". */
class NearbyPeersTest {

    private fun beacon(id: Int, servesAudio: Boolean = true) = OffGridBeacon(
        deviceId = id,
        fingerprint = ByteArray(OffGridBeacon.FINGERPRINT_SIZE) { id.toByte() },
        servesAudio = servesAudio
    )

    @Test
    fun `one device seen many times is listed once`() {
        val peers = NearbyPeers()
        repeat(20) { peers.sighted(beacon(1), rssi = -50, nowMs = 1_000L + it) }
        assertEquals(1, peers.current(1_100L).size)
    }

    /** The nearest device is very nearly always the one the user means. */
    @Test
    fun `the strongest signal is listed first`() {
        val peers = NearbyPeers()
        peers.sighted(beacon(1), rssi = -80, nowMs = 0)
        peers.sighted(beacon(2), rssi = -40, nowMs = 0)
        assertEquals(2, peers.current(0).first().beacon.deviceId)
    }

    /** A phone that leaves says nothing; absence is the only signal there is. */
    @Test
    fun `a device that stops advertising drops off the list`() {
        val peers = NearbyPeers(staleAfterMs = 1_000L)
        peers.sighted(beacon(1), rssi = -50, nowMs = 0)
        assertEquals(1, peers.current(500L).size)
        assertTrue(peers.current(2_000L).isEmpty())
    }

    /** Offering a listener as a source produces a connection that fails after the user picks it. */
    @Test
    fun `devices that will not serve audio are not offered as sources`() {
        val peers = NearbyPeers()
        peers.sighted(beacon(1, servesAudio = false), rssi = -40, nowMs = 0)
        peers.sighted(beacon(2, servesAudio = true), rssi = -80, nowMs = 0)

        val servers = peers.servers(0)
        assertEquals(1, servers.size)
        assertEquals(2, servers.first().beacon.deviceId)
    }

    @Test
    fun `a brief gap in advertisements does not make the list flicker`() {
        val peers = NearbyPeers()
        peers.sighted(beacon(1), rssi = -50, nowMs = 0)
        assertFalse(peers.current(NearbyPeers.STALE_AFTER_MS - 1).isEmpty())
    }
}
