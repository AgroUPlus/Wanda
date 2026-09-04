package com.wander.android.data.sources.agro

import androidx.compose.runtime.Immutable

/** Where the viewer stands with another account, as the server reports it. */
internal enum class FriendState {
    /** Not connected. Also what a block looks like from the blocked side, by design. */
    NONE,
    PENDING,
    ACCEPTED;

    internal companion object {
        fun parse(raw: String?): FriendState = when (raw?.lowercase()) {
            "accepted" -> ACCEPTED
            "pending" -> PENDING
            else -> NONE
        }
    }
}

/**
 * Another account's public card.
 *
 * [showNowPlaying] and [showStats] are the *subject's* switches, not the viewer's permissions —
 * they are carried so the UI can say "they keep this private" rather than drawing an empty chart
 * and leaving the reason to guesswork.
 */
@Immutable
internal data class AgroProfile(
    val username: String,
    val displayName: String?,
    val bio: String?,
    val avatarUrl: String?,
    val createdAt: String,
    val friendState: FriendState,
    /** True when it was the viewer who sent the unanswered request. Only meaningful when PENDING. */
    val outgoing: Boolean,
    val showNowPlaying: Boolean,
    val showStats: Boolean,
    val discoverable: Boolean,
    /** Whether this account lets friends read its listening history. */
    val showActivity: Boolean = false,
    /** X25519 public identity key for E2EE drops. */
    val publicKey: String? = null
) {
    /** What to put on screen. Falls back to the username, which always exists. */
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/**
 * What a friend is playing.
 *
 * Never persisted. Someone else's listening is theirs, and writing it to this device's disk would
 * outlive both their session and any decision they later make about who can see it.
 */
@Immutable
internal data class AgroFriendNowPlaying(
    val username: String,
    val trackUri: String,
    val trackTitle: String,
    val artistName: String,
    val albumName: String?,
    val artworkUrl: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val updatedAt: String,
    /** Which of the host's devices is playing, so a direct transfer has something to address. */
    val deviceId: String? = null,
    /** SHA-256 of the host's file, when they have one. Null for anything they are streaming. */
    val contentHash: String? = null,
    /**
     * Where to reach the host's device on this network, and the token to present when doing so.
     *
     * Both are set only when the server judged the two devices to share a local network. Neither
     * is usable without the other: the address alone reaches a server that will refuse it.
     */
    val peerLanAddress: String? = null,
    val peerLanToken: String? = null,
    /**
     * The session's real metadata, sealed to this device's key, when the friend sealed it.
     *
     * Present means the plaintext [trackTitle] and [artistName] above are a placeholder and this is
     * where the truth is. Opened at the source boundary, so nothing above this layer has to know a
     * sealed session from an ordinary one.
     */
    val encryptedPresence: String? = null,
    /**
     * True when this session arrived sealed and could not be opened.
     *
     * Distinct from an ordinary session: the app knows something is playing but not what, and says
     * so rather than showing the placeholder as if it were a track name. Happens when a friend has
     * sealed to a device key this install no longer holds — a reinstall, or a restore.
     */
    val isLocked: Boolean = false
)

/** A friend, with whatever they are playing when they allow that to be seen. */
@Immutable
internal data class AgroFriend(
    val profile: AgroProfile,
    val nowPlaying: AgroFriendNowPlaying?
)

/** How far two accounts' listening overlaps. */
@Immutable
internal data class AgroTasteMatch(
    /** 0–100: the share of the smaller history that both accounts have in common, by artist. */
    val score: Int,
    val sharedArtists: List<StatEntry>,
    val sharedTracks: List<StatEntry>
)

/** A listen-along session, from either end. */
@Immutable
internal data class AgroListenAlong(
    val host: String,
    val listeners: List<String>,
    val nowPlaying: AgroFriendNowPlaying?
)

/** The switches that decide what other people can see. */
@Immutable
internal data class AgroVisibility(
    val showNowPlaying: Boolean,
    val showStats: Boolean,
    val discoverable: Boolean,
    /**
     * Whether friends may read this account's listening *history* — the activity feed.
     *
     * Its own switch rather than part of `showStats`, because a total and a timeline are different
     * disclosures. Defaults off, like the rest.
     */
    val showActivity: Boolean = false
)

/**
 * A short-lived code for adding the account that minted it as a friend.
 *
 * [ttlSeconds] counts down from the moment the server answered, rather than being derived from an
 * expiry timestamp: the device's clock is not the server's, and a panel re-minting on a drifting
 * timer would either show a dead code or thrash.
 */
@Immutable
internal data class AgroFriendCode(
    val code: String,
    val ttlSeconds: Long
)
