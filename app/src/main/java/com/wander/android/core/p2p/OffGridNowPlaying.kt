package com.wander.android.core.p2p

import kotlinx.serialization.Serializable

/**
 * What a peer is playing, carried over the radio link instead of through a server.
 *
 * Listen-along is normally driven by Agro: the host reports what it is playing, the server pushes a
 * frame, the listener follows it. Off-grid there is no server to report to and no frame to push, so
 * the tier that exists precisely for having no internet could carry the *audio* and nothing that
 * said which audio to ask for. This is that missing half.
 *
 * Deliberately not [com.wander.android.data.sources.agro.AgroFriendNowPlaying]. That shape carries
 * a username, a LAN address and a bearer token minted by Agro — none of which exist here, and
 * three of which would be misleading if left null in a type whose whole purpose is to say who and
 * where. The listener converts, and the conversion is where the off-grid answers to those
 * questions are supplied.
 */
@Serializable
internal data class OffGridNowPlaying(
    val title: String,
    val artist: String,
    val album: String? = null,
    /**
     * SHA-256 of the host's file, when it has one.
     *
     * Null for anything the host is streaming, and then the listener cannot fetch it: the peer
     * stream is addressed by hash, and there is no network to fall back to. A null here is the
     * honest form of "you cannot follow this one".
     */
    val contentHash: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    /** Cleared to true when the host is playing nothing, so a listener stops rather than guesses. */
    val idle: Boolean = false
) {
    companion object {
        /** What a host answers with when it is playing nothing at all. */
        val IDLE = OffGridNowPlaying(title = "", artist = "", idle = true)
    }
}
