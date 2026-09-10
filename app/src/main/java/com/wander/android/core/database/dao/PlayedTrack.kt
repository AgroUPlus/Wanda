package com.wander.android.core.database.dao

/**
 * One play, joined to the track it was.
 *
 * The statistics screen's row shape. Separate from [PendingScrobble], which is the *outbox* row:
 * that one carries the genre a scrobble has to report and no artwork, this one carries the artwork
 * a screen has to draw and no genre. Merging them would mean every scrobble upload also read a
 * column it has no use for.
 */
data class PlayedTrack(
    val playedAt: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long
)
