package com.wander.android.core.p2p

/**
 * What one device shouts into the room over Bluetooth Low Energy.
 *
 * A BLE advertisement is tiny — a service-data field leaves about twenty usable bytes — so this is
 * a fixed layout rather than anything self-describing. There is no room for a device name, and
 * that is just as well: an advertisement is broadcast continuously to anyone within range, and a
 * beacon carrying "Théo's Pixel" would let a shop, a station or a stranger follow a person around
 * by listening for it.
 *
 * So it carries the least that still works:
 *
 * ```
 * byte  0      protocol version
 * bytes 1..4   short device id, derived from the identity key
 * bytes 5..12  identity fingerprint, the first 8 bytes of the X25519 public key
 * byte  13     flags — currently only whether this device will serve audio
 * ```
 *
 * The fingerprint is truncated because the advertisement cannot hold a whole key, and it does not
 * need to: it is a *hint* that lets a scanner recognise a device it has paired with before. The
 * real key exchange happens over the link once one exists, where there is room to send all 32
 * bytes and to prove possession of the private half. Nothing here is authentication and nothing
 * here is trusted — treating a truncated fingerprint as proof of identity would be forgeable by
 * anyone who has ever seen the beacon.
 */
internal data class OffGridBeacon(
    val deviceId: Int,
    val fingerprint: ByteArray,
    val servesAudio: Boolean
) {

    fun toBytes(): ByteArray {
        val bytes = ByteArray(SIZE)
        bytes[0] = VERSION
        bytes[1] = (deviceId ushr 24).toByte()
        bytes[2] = (deviceId ushr 16).toByte()
        bytes[3] = (deviceId ushr 8).toByte()
        bytes[4] = deviceId.toByte()
        for (i in 0 until FINGERPRINT_SIZE) {
            bytes[5 + i] = fingerprint.getOrElse(i) { 0 }
        }
        bytes[13] = if (servesAudio) 1 else 0
        return bytes
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OffGridBeacon) return false
        return deviceId == other.deviceId &&
            servesAudio == other.servesAudio &&
            fingerprint.contentEquals(other.fingerprint)
    }

    override fun hashCode(): Int {
        var result = deviceId
        result = 31 * result + fingerprint.contentHashCode()
        result = 31 * result + servesAudio.hashCode()
        return result
    }

    internal companion object {
        const val VERSION: Byte = 1
        const val FINGERPRINT_SIZE = 8
        const val SIZE = 14

        /**
         * Wanda's service UUID. Scanners filter on it, so a phone is not woken by every fitness
         * tracker in the room — which is the difference between BLE discovery costing nothing and
         * costing a battery.
         *
         * A randomly generated 128-bit UUID, not a short one in the Bluetooth base range: those
         * are SIG-assigned, and every character of a UUID must be hexadecimal.
         */
        const val SERVICE_UUID = "b7d4a1e6-3c92-4f08-9a5d-6e21c8f47b0a"

        /**
         * Reads a beacon, or null if this is not one of ours.
         *
         * Null rather than an exception: a BLE scan picks up whatever is in the air, and malformed
         * or foreign payloads are the normal case, not an error.
         */
        fun fromBytes(bytes: ByteArray?): OffGridBeacon? {
            if (bytes == null || bytes.size < SIZE) return null
            if (bytes[0] != VERSION) return null
            val deviceId = (bytes[1].toInt() and 0xFF shl 24) or
                (bytes[2].toInt() and 0xFF shl 16) or
                (bytes[3].toInt() and 0xFF shl 8) or
                (bytes[4].toInt() and 0xFF)
            return OffGridBeacon(
                deviceId = deviceId,
                fingerprint = bytes.copyOfRange(5, 5 + FINGERPRINT_SIZE),
                servesAudio = bytes[13].toInt() != 0
            )
        }

        /**
         * A short id derived from the device's own identity key.
         *
         * Derived rather than random so it survives a restart without being stored, and so two
         * devices cannot collide on an id that means nothing. It is not a secret — it is broadcast
         * — and it is not an identity either; see the note above on why nothing here is trusted.
         */
        fun deviceIdFrom(publicKey: ByteArray): Int {
            if (publicKey.size < 4) return 0
            return (publicKey[0].toInt() and 0xFF shl 24) or
                (publicKey[1].toInt() and 0xFF shl 16) or
                (publicKey[2].toInt() and 0xFF shl 8) or
                (publicKey[3].toInt() and 0xFF)
        }

        fun fingerprintFrom(publicKey: ByteArray): ByteArray =
            publicKey.copyOf(FINGERPRINT_SIZE)
    }
}
