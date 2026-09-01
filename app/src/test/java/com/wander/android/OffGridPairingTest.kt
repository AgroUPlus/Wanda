package com.wander.android

import com.wander.android.core.p2p.OffGridBeacon
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The check that decides whether a radio link reached the device the user picked.
 *
 * A Wi-Fi Direct group is formed with whatever the framework negotiates, and the fourteen-byte
 * beacon has no room for a MAC address — so there is no way to *ask* for one peer. The link comes
 * up and only afterwards can this device find out whose it is, by recomputing the fingerprint from
 * the identity key the peer returns and comparing it to the beacon that was tapped.
 *
 * That comparison is the whole of the guarantee. If it ever stopped being exact, connecting to a
 * friend and connecting to a stranger in the same room would look identical, and the stranger would
 * be handed a grant. This pins the arithmetic rather than the plumbing around it, which is what a
 * JVM test can hold onto — everything above it is framework.
 */
class OffGridPairingTest {

    private val key = ByteArray(32) { (it + 1).toByte() }

    private fun beaconFor(publicKey: ByteArray) = OffGridBeacon(
        deviceId = OffGridBeacon.deviceIdFrom(publicKey),
        fingerprint = OffGridBeacon.fingerprintFrom(publicKey),
        servesAudio = true
    )

    /** Mirrors `OffGridPairing.matchesBeacon`, which is private and takes base64. */
    private fun matches(publicKey: ByteArray, expected: OffGridBeacon): Boolean {
        if (publicKey.size < OffGridBeacon.FINGERPRINT_SIZE) return false
        return OffGridBeacon.fingerprintFrom(publicKey).contentEquals(expected.fingerprint)
    }

    @Test
    fun `the peer that advertised is the peer that answers`() {
        assertTrue(matches(key, beaconFor(key)))
    }

    @Test
    fun `a different device fails the check`() {
        val other = ByteArray(32) { (it + 99).toByte() }
        assertFalse(matches(other, beaconFor(key)))
    }

    /**
     * The fingerprint is a prefix, so a key that differs only past it must still be rejected — or
     * rather, must be *known* to be accepted. Eight bytes is not a collision guarantee and is not
     * asked to be one: it separates the handful of devices in a room. This pins that boundary
     * explicitly so nobody later mistakes it for a cryptographic identity check.
     */
    @Test
    fun `only the first bytes are compared`() {
        val sharedPrefix = key.copyOf().also { it[OffGridBeacon.FINGERPRINT_SIZE] = 0x7F }
        assertTrue(matches(sharedPrefix, beaconFor(key)))
    }

    @Test
    fun `a truncated key cannot match`() {
        assertFalse(matches(ByteArray(4), beaconFor(key)))
    }
}
