package com.wander.android.data.sources.agro

import androidx.compose.runtime.Immutable

/**
 * A device registered with the Agro server (`NodePayload`).
 *
 * [isOnline] is the server's own judgement, not ours: it is true only when the node was last heard
 * from within 45 seconds, which is why a client that plays a four-minute track without reporting in
 * disappears from the list.
 */
@Immutable
data class AgroNode(
    val deviceId: String,
    val petname: String,
    val clientType: String,
    val currentTrack: String?,
    val isOnline: Boolean
)

/**
 * The one session the server holds per user (`HandoffState`) — not per device. Resuming always
 * means "the most recent session", whichever device produced it.
 */
@Immutable
data class AgroHandoffState(
    val trackUri: String,
    val trackTitle: String,
    val artistName: String,
    val albumName: String?,
    val artworkUrl: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val deviceId: String,
    val updatedAt: String,
    /** The rest of the session, in play order. Empty when the sender predates queue handoff. */
    val queue: List<AgroHandoffTrack> = emptyList(),
    /** Where [queue] was playing, or -1 when no queue travelled. */
    val queueIndex: Int = -1
)

/** One entry of a handed-over queue, as the sending client described it. */
@Immutable
data class AgroHandoffTrack(
    val trackUri: String,
    val trackTitle: String,
    val artistName: String,
    val albumName: String?,
    val artworkUrl: String?
)

/**
 * The portable slice of configuration Agro carries between clients (`SyncedSettingsPayload`).
 * Deliberately no credentials: the Navidrome token is never in here, so a synced server URL still
 * needs a sign-in on this device.
 */
@Immutable
data class AgroSyncedSettings(
    val serverUrl: String?,
    val serverUsername: String?
)

/**
 * One frame from `/ws/sync`, reduced to what this app acts on.
 *
 * The socket is a hint channel, never a source of state: every message means "something changed",
 * and the app answers by asking the server what it should now know. So a dropped or duplicated
 * frame costs a redundant query rather than a wrong screen.
 */
sealed interface AgroLiveMessage {
    /** The playing session or the device list moved. */
    data object Session : AgroLiveMessage

    /** The library changed, or this device is being offered something it lacks. */
    data class Library(val newTrackCount: Int, val albums: List<String>) : AgroLiveMessage
}
