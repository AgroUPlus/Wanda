package com.wander.android.core.p2p

/**
 * A live off-grid link, as either end of it sees it.
 *
 * One type for both roles on purpose. The two ends of a Wi-Fi Direct pair do different work — one
 * negotiated the group, the other answered — but "am I connected, to whom, and since when" is the
 * same question on both screens, and giving each side its own shape is what let one of them go
 * without an answer at all.
 */
internal data class OffGridLink(
    /** The peer's beacon device id, which is what the row the user tapped is keyed by. */
    val deviceId: Int,
    val role: Role,
    val sinceMs: Long
) {
    internal enum class Role {
        /** This device tapped the other one and raised the group. */
        INITIATED,

        /** The other device tapped this one. Previously invisible here. */
        ACCEPTED
    }
}
