package com.wander.android.core.database.dao

import androidx.room.Embedded
import com.wander.android.core.database.entity.TrackEntity

/**
 * One play, joined to the track it was — the paged History screen's row shape.
 *
 * Separate from [PlayedTrack]/[PendingScrobble]: those are narrow projections for a background
 * job, while this carries the whole [TrackEntity] a row needs to render (artwork, subtitle,
 * like state) plus the two columns that make it a *play* rather than a track — [historyId] for a
 * stable paging key and [playedAt] for the timestamp no [TrackEntity] on its own remembers.
 *
 * Deliberately one row per play, not deduplicated by track: [HistoryDao.getRecentlyPlayedTracksFlow]
 * already covers "what have I played" as a collection. This is "what did I play, and when" as a
 * log, which is what pagination and the date/time dividers need — a `GROUP BY` would make the
 * result set change shape as new plays arrive, which `PagingSource` keyset paging cannot follow.
 */
data class HistoryTrackEntry(
    val historyId: Long,
    val playedAt: Long,
    @Embedded val track: TrackEntity
)
