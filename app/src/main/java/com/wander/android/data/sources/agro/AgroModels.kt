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
    val serverUsername: String?,
    /**
     * Share-link forwarding, configured once on the server so every client agrees. Not a
     * credential and not encrypted: the domain is printed in every link it produces, and
     * [shareHosts] is the allowlist the server's own `/listen` route enforces.
     */
    val shareDomain: String?,
    val shareHosts: String?,
    val shareEnabled: Boolean
)

/**
 * One frame from `/ws/sync`, reduced to what this app acts on.
 *
 * The socket is a hint channel, never a source of state: every message means "something changed",
 * and the app answers by asking the server what it should now know. So a dropped or duplicated
 * frame costs a redundant query rather than a wrong screen.
 */
/** Something a person did to the friendship, as opposed to something they played. */
internal sealed interface FriendEvent {
    /** Someone asked to be your friend. */
    data class Requested(val from: String) : FriendEvent

    /** Someone answered a request you sent. */
    data class Accepted(val by: String) : FriendEvent

    /** A request you sent is gone — declined, or the friendship was ended. */
    data class Declined(val by: String) : FriendEvent
}

/** The room's current track, as a live frame carries it. */
internal data class AgroJamNowPlaying(
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val positionMs: Long
)

internal sealed interface AgroLiveMessage {
    /** The playing session or the device list moved. */
    data object Session : AgroLiveMessage

    /**
     * A synced setting changed on another of this account's devices.
     *
     * Carries nothing. The values are small, the server owns them, and a payload here would be a
     * second place they could be read from — one that goes stale the moment a frame is missed.
     * The app re-asks instead.
     */
    data object Settings : AgroLiveMessage

    /** The library changed, or this device is being offered something it lacks. */
    data class Library(val newTrackCount: Int, val albums: List<String>) : AgroLiveMessage

    /**
     * A friend started playing something, or answered a request.
     *
     * Carries no *presence* payload: presence is small enough to re-read in one query, and a frame
     * that carried its own copy would be a second place for the visibility rules to be applied —
     * the server's decision about what this account may see belongs in exactly one code path.
     *
     * [event] is different in kind. It names who did what, which is not gated data — you are
     * already entitled to know that someone asked to be your friend, or answered when you did.
     * Without it the app could tell that *something* changed but never say what, which is the
     * difference between a list that quietly reorders itself and one that says "beta accepted your
     * friend request".
     */
    data class Friends(
        val event: FriendEvent? = null,
        /**
         * The friend's new track, when this frame was a presence update.
         *
         * Applied directly rather than triggering a re-query. The server has *already* applied the
         * visibility rules — it decided who this frame goes to at all — so acting on the payload
         * is not a second place those rules live. Re-querying instead meant a round trip per track
         * change, and one that a rapidly-skipping friend could cancel before it landed, leaving
         * the previous song on screen.
         */
        val presence: AgroFriendNowPlaying? = null
    ) : AgroLiveMessage

    /** Somebody suggested, approved or removed something. The queue itself changed. */
    data object JamUpdated : AgroLiveMessage

    /**
     * The room moved to a new track.
     *
     * Decided by the server on its own clock, so this is an instruction rather than news: the
     * device plays what it names, at the position it names. Null means the room fell silent.
     */
    data class JamNowPlayingFrame(val nowPlaying: AgroJamNowPlaying?) : AgroLiveMessage

    /**
     * The account being listened along to moved.
     *
     * This one *does* carry its payload, because acting on it is the point: a player cannot follow
     * a position it has to make another round trip to learn. [stopped] means the session ended —
     * the host closed their now-playing switch, or the friendship did.
     */
    data class ListenAlong(
        val host: String,
        val trackUri: String,
        val trackTitle: String,
        val artistName: String,
        val albumName: String?,
        val artworkUrl: String?,
        val positionMs: Long,
        val isPlaying: Boolean,
        val stopped: Boolean
    ) : AgroLiveMessage

    /**
     * A friend handed this account a song.
     *
     * Carries its payload, on the same reasoning as [ListenAlong] and the presence frame: the
     * server already decided this account may see it — it chose the recipient — so acting on what
     * arrived is not a second place the visibility rules live. It also means the notification can
     * name the track without a round trip, which matters because the socket is only open while the
     * app is foregrounded and the round trip might not finish before it closes.
     */
    data class TrackDrop(val drop: AgroDrop) : AgroLiveMessage
}
