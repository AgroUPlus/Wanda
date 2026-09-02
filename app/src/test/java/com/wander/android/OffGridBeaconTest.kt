package com.wander.android

import com.wander.android.core.p2p.NearbyPeers
import com.wander.android.core.p2p.OffGridBeacon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

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
        // The budget, spelled out, because the old bound of 20 was guesswork and passed while the
        // real packet was 50 bytes and refused by the radio in silence.
        //
        //   31  a legacy BLE advertisement, in total
        //  - 3  the flags structure Android prepends
        //  -18  the service-data header: length, type, and a 128-bit UUID
        //  ----
        //   10  left for the payload
        val budget = 31 - 3 - (2 + 16)
        assertTrue(
            "beacon is ${beacon().toBytes().size} bytes, budget is $budget",
            beacon().toBytes().size <= budget
        )
    }

    /** Nothing in the payload should be able to name a person or a phone. */
    @Test
    fun `a beacon carries no name`() {
        val bytes = beacon().toBytes()
        assertEquals(OffGridBeacon.SIZE, bytes.size)
        // A version, a fingerprint and one flag. There is no room for a name and no field for one.
        assertEquals(10, bytes.size)
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

    /**
     * The regression that took the whole app down on launch.
     *
     * `0000w4nd-0000-1000-8000-00805f9b34fb` spelled a word where hex was required. `BleDiscovery`
     * parses this in a field initialiser and is a `@Singleton`, so the throw came out of the Hilt
     * graph before any off-grid code ran — a dead app, from a constant nothing else looked at.
     */
    @Test
    fun `the service uuid is a uuid`() {
        assertEquals(OffGridBeacon.SERVICE_UUID, UUID.fromString(OffGridBeacon.SERVICE_UUID).toString())
    }

    /**
     * A legacy advertisement is thirty-one bytes, and one that does not fit is refused outright.
     *
     * Counted rather than measured, because `AdvertiseData` cannot be built off a device: the
     * service-data field costs two bytes of AD header plus the sixteen of a 128-bit UUID, and the
     * framework prepends a three-byte flags field of its own. This is what caught the packet that
     * named the UUID twice and came to fifty bytes.
     */
    @Test
    fun `the advertisement fits in a legacy packet`() {
        val serviceDataField = AD_HEADER + UUID_128_BYTES + OffGridBeacon.SIZE
        assertTrue(FLAGS_FIELD + serviceDataField <= LEGACY_ADVERTISEMENT_BYTES)
    }

    /** Derived from the key so it survives a restart without being stored anywhere. */
    @Test
    fun `the device id is stable for one identity and differs between two`() {
        val other = ByteArray(32) { (it + 9).toByte() }
        assertEquals(OffGridBeacon.deviceIdFrom(key), OffGridBeacon.deviceIdFrom(key))
        assertTrue(OffGridBeacon.deviceIdFrom(key) != OffGridBeacon.deviceIdFrom(other))
    }

    private companion object {
        const val LEGACY_ADVERTISEMENT_BYTES = 31
        const val AD_HEADER = 2
        const val UUID_128_BYTES = 16
        const val FLAGS_FIELD = 3
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
