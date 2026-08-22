package com.wander.android.data.sources.agro

import androidx.compose.runtime.Immutable

/**
 * A song somebody handed you, or one you handed out.
 *
 * Unlike presence, this **is** persisted. A drop is a message: it is meant to still be there
 * tomorrow, and losing the inbox on every process death would make the feature useless. See
 * `DropEntity` for the stored form.
 *
 * [contentHash] and [trackUri] are both optional and both often absent. A drop describes a track
 * rather than referring to one, because the sender may have been playing something from a backend
 * this device has never heard of — the title and artist are the only fields that always mean
 * something here.
 */
@Immutable
internal data class AgroDrop(
    val id: String,
    /** Who sent it. Empty on a drop this account sent, where [toUser] is the interesting end. */
    val fromUser: String,
    val toUser: String,
    val trackTitle: String,
    val artistName: String,
    val albumName: String?,
    val artworkUrl: String?,
    val contentHash: String?,
    val trackUri: String?,
    val note: String?,
    val createdAt: String,
    /**
     * When this account read it, or null while unread.
     *
     * Always null on a drop *sent* by this account — the server blanks it deliberately, because
     * whether somebody opened what you gave them is information about them. Do not build a
     * "seen" indicator on top of this; there is nothing behind it.
     */
    val readAt: String?,
    val archived: Boolean
) {
    val isUnread: Boolean get() = readAt == null && !archived
}

/** One entry in the friend activity feed. */
@Immutable
internal data class AgroFeedItem(
    val username: String,
    val at: String,
    /** `MILESTONE`, `ON_REPEAT`, `NEW_FAVOURITE`, or something newer than this build. */
    val kind: String,
    /**
     * The sentence to show.
     *
     * Composed by the server so that every client says the same thing. A client that renders its
     * own phrasing from [kind] and [count] will drift from the others the first time the rule
     * behind an event changes.
     */
    val summary: String,
    val artist: String,
    val title: String?,
    val count: Long
)

/** The circle's shared recap for one period. */
@Immutable
internal data class AgroRecap(
    val period: String,
    /** You, plus the friends who have opened their statistics. Nobody else is in it. */
    val members: List<String>,
    val anthem: AgroAnthem?,
    val topTracks: List<StatEntry>,
    val topArtists: List<StatEntry>,
    val trendsetter: AgroTrendsetter?,
    val matrix: List<AgroTasteMatrixEntry>
)

@Immutable
internal data class AgroAnthem(
    val title: String,
    val artist: String,
    val plays: Long,
    val byMember: List<StatEntry>
)

@Immutable
internal data class AgroTrendsetter(
    val username: String,
    /** How many of the circle's top tracks they reached before anybody else did. */
    val firsts: Long,
    val examples: List<String>
)

@Immutable
internal data class AgroTasteMatrixEntry(
    val a: String,
    val b: String,
    val score: Int
)
