package com.wander.android.core.p2p

/**
 * Who is in the room, as far as anyone can tell from radio.
 *
 * A BLE scan produces a stream of sightings, not a list of devices: the same phone is seen dozens
 * of times a second at varying signal strength, and a phone that has left the room simply stops
 * being seen — there is no goodbye. This turns that into something a UI can show, which means
 * three decisions the scan itself does not make.
 *
 * **Dedup by device id, keeping the newest sighting.** Otherwise one device fills the list.
 *
 * **Expiry, because absence is silence.** A peer not seen for [STALE_AFTER_MS] is dropped. The
 * window is generous on purpose: BLE advertisements are missed constantly, and a list that flickers
 * as a phone is briefly not heard is worse than one that holds a departed device for a few seconds.
 *
 * **Order by signal, strongest first.** The nearest device is very nearly always the one the user
 * means, because the person they are handing music to is the person standing next to them.
 */
internal class NearbyPeers(private val staleAfterMs: Long = STALE_AFTER_MS) {

    /** One device, as last heard. */
    internal data class Peer(
        val beacon: OffGridBeacon,
        /** Signal strength in dBm. Closer to zero is nearer. */
        val rssi: Int,
        val lastSeenAtMs: Long
    )

    private val seen = LinkedHashMap<Int, Peer>()

    /** Records a sighting. Returns true when this changed what a UI would show. */
    fun sighted(beacon: OffGridBeacon, rssi: Int, nowMs: Long): Boolean {
        val existing = seen[beacon.deviceId]
        seen[beacon.deviceId] = Peer(beacon, rssi, nowMs)
        return existing == null || existing.beacon != beacon
    }

    /** Everyone currently in the room, nearest first. */
    fun current(nowMs: Long): List<Peer> {
        seen.entries.removeAll { nowMs - it.value.lastSeenAtMs > staleAfterMs }
        return seen.values.sortedByDescending { it.rssi }
    }

    /**
     * The peers worth offering as a transfer target: those that said they will serve audio.
     *
     * A device that is only listening still advertises — it wants to be found by whoever is
     * sharing — but offering it as a source would produce a connection that fails at the first
     * request, after the user has already chosen it.
     */
    fun servers(nowMs: Long): List<Peer> = current(nowMs).filter { it.beacon.servesAudio }

    fun clear() = seen.clear()

    internal companion object {
        /** How long a device stays listed after its last advertisement. */
        const val STALE_AFTER_MS = 12_000L
    }
}
