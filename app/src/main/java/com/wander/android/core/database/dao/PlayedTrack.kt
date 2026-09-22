package com.wander.android.core.database.dao

/**
 * One play, joined to the track it was.
 *
 * The screen's row shape. Still separate from [PendingScrobble], which is the *outbox* row: this
 * one carries the artwork and the track id a screen needs to *show* a play rather than name it,
 * and the outbox has no use for either. Merging them would mean every scrobble upload also read
 * columns it never sends.
 *
 * [genre] is in both because both need it — the outbox reports it, and Agro Replay counts it for
 * the genres card on a device with no server paired.
 */
data class PlayedTrack(
    val playedAt: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val genre: String?
)
